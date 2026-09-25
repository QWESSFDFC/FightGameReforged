# =============================================================================
# check-sources.ps1 -- static self-check for the Java sources.
#
# WHY THIS EXISTS
#   The AI cannot compile (see TIPS_FOR_LLM.md section 0), so every javac error
#   costs the user a full build round-trip. This script catches the CLASS of
#   mistakes that kept slipping through:
#     1. unbalanced braces (a bad multi-line edit truncating a method)
#     2. a UTF-8 BOM in a .java file (javac chokes on it)
#     3. a MISSING import (identifier used but neither imported, same-package,
#        nested in this file, nor in java.lang)
#     4. an UNUSED import (dead code left behind by refactors)
#     5. a DUPLICATE field declaration (a bad edit pasting a block twice)
#
#   It is deliberately pure ASCII: the project's .ps1 files are UTF-8-with-BOM,
#   but Chinese text inside a script gets mangled by some consoles, so this one
#   avoids the problem entirely. Messages are English on purpose.
#
# USAGE
#   powershell -ExecutionPolicy Bypass -File .\check-sources.ps1
#
# OPTIONAL SWITCH (top of file, not a param block: a param block must be the very
# first statement, which breaks dot-sourcing / script-block execution):
#   $CheckDuplicateMethods = $true    -> also flag identical method signatures.
#   OFF by default: brace depth cannot tell two nested classes apart, so it has
#   false positives (e.g. two inner classes each defining copy()).
#
# EXIT CODE
#   0 = clean, 1 = problems found (safe to wire into a pre-commit hook)
# =============================================================================

# --- optional switches (edit here) ----------------------------------------------
$CheckDuplicateMethods = $false

$ErrorActionPreference = 'Stop'
$root = Join-Path $PSScriptRoot 'src'
if (-not (Test-Path $root)) { Write-Host "src/ not found next to this script: $root" -ForegroundColor Red; exit 1 }

# --- diagnostics state ----------------------------------------------------------
# A single state object instead of several global counters: globals behaved
# differently depending on how the file is executed (as a script vs. as a
# script block), which once made the summary print an empty problem count.
$diag = [pscustomobject]@{
    Problems = New-Object 'System.Collections.Generic.List[string]'
    Warnings = New-Object 'System.Collections.Generic.List[string]'
}
function Report([string]$kind, [string]$text) {
    Write-Host ("[{0}] {1}" -f $kind, $text) -ForegroundColor Yellow
    [void]$diag.Problems.Add(($kind + ' ' + $text))
}
function ReportWarning([string]$kind, [string]$text) {
    Write-Host ("[{0}] {1}" -f $kind, $text) -ForegroundColor DarkYellow
    [void]$diag.Warnings.Add(($kind + ' ' + $text))
}

# --- load all sources once ------------------------------------------------------
$files = Get-ChildItem -Path $root -Recurse -Filter *.java -File
$content = @{}
foreach ($f in $files) { $content[$f.FullName] = [System.IO.File]::ReadAllText($f.FullName) }
Write-Host ("Java files: {0}" -f $files.Count)

# every simple class name defined anywhere in the project, plus its package
$allClasses = @{}
$pkgOfClass = @{}
foreach ($f in $files) {
    $allClasses[$f.BaseName] = $true
    $decl = [regex]::Match($content[$f.FullName], '(?m)^package\s+([\w.]+);')
    if ($decl.Success) { $pkgOfClass[$f.BaseName] = $decl.Groups[1].Value }
}

$javaLang = @(
    # core
    'String','System','Object','Override','Math','Long','Integer','Double','Boolean','Byte','Short','Float',
    'Character','StringBuilder','StringBuffer','CharSequence','Enum','Record','Number','Void','Class','Deprecated',
    'SuppressWarnings','FunctionalInterface','SafeVarargs','Cloneable','AutoCloseable','Appendable','Iterable',
    'Comparable','Runnable','Thread','Process','ProcessBuilder','Runtime','Package','Module',
    # java.lang exceptions / errors (these need no import either)
    'Throwable','Error','Exception','RuntimeException','IllegalArgumentException','IllegalStateException',
    'UnsupportedOperationException','NullPointerException','IndexOutOfBoundsException','ArrayIndexOutOfBoundsException',
    'NumberFormatException','ClassCastException','ArithmeticException','InterruptedException','CloneNotSupportedException',
    'NoSuchElementException','SecurityException','AssertionError','OutOfMemoryError','StackOverflowError',
    # java.lang functional interfaces
    'Supplier','Consumer','Function','BiFunction','Predicate','BiPredicate','UnaryOperator','BinaryOperator'
)

# --- 1. BOM ---------------------------------------------------------------------
$bomCount = 0
foreach ($f in $files) {
    $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        Report 'BOM' ($f.FullName + '  (.java must be UTF-8 WITHOUT BOM)')
        $bomCount++
    }
}

# --- 2. braces ------------------------------------------------------------------
# minimal state machine: skips comments, string and char literals
function Test-Braces([string]$text, [string]$path) {
    $depth = 0; $inStr = $false; $inChar = $false; $inLine = $false; $inBlock = $false; $esc = $false
    for ($i = 0; $i -lt $text.Length; $i++) {
        $c = $text[$i]
        $n = if ($i + 1 -lt $text.Length) { $text[$i + 1] } else { [char]0 }
        if ($inLine)  { if ($c -eq "`n") { $inLine = $false }; continue }
        if ($inBlock) { if ($c -eq '*' -and $n -eq '/') { $inBlock = $false; $i++ }; continue }
        if ($inStr)   { if ($esc) { $esc = $false } elseif ($c -eq '\') { $esc = $true } elseif ($c -eq '"') { $inStr = $false }; continue }
        if ($inChar)  { if ($esc) { $esc = $false } elseif ($c -eq '\') { $esc = $true } elseif ($c -eq "'") { $inChar = $false }; continue }
        if ($c -eq '/' -and $n -eq '/') { $inLine = $true; $i++; continue }
        if ($c -eq '/' -and $n -eq '*') { $inBlock = $true; $i++; continue }
        if ($c -eq '"') { $inStr = $true; continue }
        if ($c -eq "'") { $inChar = $true; continue }
        if ($c -eq '{') { $depth++ }
        elseif ($c -eq '}') {
            $depth--
            if ($depth -lt 0) { Report 'BRACE' ($path + '  extra closing brace'); return }
        }
    }
    if ($depth -ne 0) { Report 'BRACE' ($path + "  unbalanced, depth=" + $depth) }
}

foreach ($f in $files) { Test-Braces $content[$f.FullName] $f.FullName }

# --- 3. imports (missing / unused) ---------------------------------------------
# rules: a capitalized identifier stands for a class when it appears in a type
# position, as a qualifier of a static member, or as a method reference owner.
# NOTE the patterns are intentionally narrow: "capitalized word at line start" is
# NOT enough (it also matches constant declarations like "private static final
# Foo BAR = ..."). Requiring a lowercase method call or "::" keeps it precise.
$missingRules = @(
    '\bnew\s+([A-Z]\w*)',                                    # new Foo(...)
    '\binstanceof\s+([A-Z]\w*)',                             # x instanceof Foo
    '\bextends\s+([A-Z]\w*)',                                # extends Foo
    '\bimplements\s+([A-Z]\w*)',                             # implements Foo
    '<\s*([A-Z]\w*)\s*[>,]',                                 # List<Foo>
    '^\s*(?:public|protected|private|static|final|\s)+([A-Z]\w*)\s+\w+\s*[;=)]',   # field / local decl
    '\bfor\s*\(\s*([A-Z]\w*)\s',                             # for (Foo x : ...)
    '\b([A-Z]\w*)\s*::',                                     # Foo::bar
    '(?<![\w.])([A-Z]\w*)\.[a-z]\w*\s*\(',                   # Foo.bar(...)
    '(?<![\w.])([A-Z]\w*)\.[A-Z]\w*'                         # Foo.CONSTANT
)

foreach ($f in $files) {
    $text = $content[$f.FullName]
    # strip comments and string literals before scanning
    $body = [regex]::Replace($text, '(?s)/\*.*?\*/', '')
    $body = [regex]::Replace($body, '(?m)//.*$', '')
    $body = [regex]::Replace($body, '"(?:[^"\\]|\\.)*"', '""')

    $imported = @{}
    $wildcardPackages = @{}
    foreach ($m in [regex]::Matches($text, '(?m)^[ \t]*import\s+(?:static\s+)?([\w.*]+)\s*;')) {
        $full = $m.Groups[1].Value
        if ($full.EndsWith('.*')) {
            $wildcardPackages[$full.Substring(0, $full.Length - 2)] = $true
        } else {
            $imported[$full.Split('.')[-1]] = $true
        }
    }
    $pkg = ([regex]::Match($text, '(?m)^package\s+([\w.]+);')).Groups[1].Value

    # classes declared in this same package (same-folder java files)
    $samePkg = @{}
    foreach ($other in Get-ChildItem -Path $f.DirectoryName -Filter *.java -File) { $samePkg[$other.BaseName] = $true }

    # nested types declared inside this very file
    $nested = @{}
    foreach ($m in [regex]::Matches($text, '(?m)^\s*(?:public|protected|private)?\s*(?:static\s+)?(?:final\s+)?(?:class|interface|enum|record)\s+(\w+)')) {
        $nested[$m.Groups[1].Value] = $true
    }
    # single-letter names are generic type parameters; ALL-CAPS names are constants
    $skipped = @{}
    foreach ($m in [regex]::Matches($body, '<\s*([A-Z])\s*[>,]')) { $skipped[$m.Groups[1].Value] = $true }
    foreach ($m in [regex]::Matches($body, '<\s*([A-Z])\s+extends')) { $skipped[$m.Groups[1].Value] = $true }
    foreach ($m in [regex]::Matches($body, '\b([A-Z][A-Z0-9_]{2,})\b')) { $skipped[$m.Groups[1].Value] = $true }

    $missing = @{}
    foreach ($rule in $missingRules) {
        foreach ($m in [regex]::Matches($body, $rule, 'Multiline')) {
            $name = $m.Groups[1].Value
            if ($skipped.ContainsKey($name)) { continue }
            if ($imported.ContainsKey($name)) { continue }
            if ($samePkg.ContainsKey($name)) { continue }
            if ($nested.ContainsKey($name)) { continue }
            if ($allClasses.ContainsKey($name)) { continue }
            if ($javaLang -contains $name) { continue }
            if ($wildcardPackages.Count -gt 0) {
                $covered = $false
                foreach ($wp in $wildcardPackages.Keys) {
                    if ($wp -like 'java.*' -or $wp -like 'javax.*' -or $wp -eq $pkgOfClass[$name]) { $covered = $true; break }
                }
                if ($covered) { continue }
            }
            $missing[$name] = $true
        }
    }
    if ($missing.Count -gt 0) {
        Report 'IMPORT?' ($f.FullName + '  uses: ' + (($missing.Keys | Sort-Object) -join ', ') + '  (not imported / same package / nested / java.lang)')
    }

    foreach ($m in [regex]::Matches($text, '(?m)^\s*import\s+([\w.*]+);')) {
        $short = $m.Groups[1].Value.Split('.')[-1]
        if ($short -eq '*') { continue }
        if ($body -notmatch ('\b' + [regex]::Escape($short) + '\b')) {
            Report 'UNUSED' ($f.FullName + '  unused import: ' + $short)
        }
    }
}

# --- 4. duplicate field declarations -------------------------------------------
# A bad edit that pastes a block twice yields "variable X is already defined" from
# javac -- invisible to every check above. Fields are compared by NAME only: two
# fields with the same name in one class always collide, whatever their types.
$fieldPattern = '^\s*(?:public|protected|private)\s+(?:static\s+)?(?:final\s+)?[\w<>\[\],.\?\s]+\s+([a-z]\w*)\s*(?:=|;)'
foreach ($f in $files) {
    $seen = New-Object 'System.Collections.Generic.HashSet[string]'
    $dups = New-Object 'System.Collections.Generic.HashSet[string]'
    foreach ($line in [System.IO.File]::ReadLines($f.FullName)) {
        if ($line -match $fieldPattern) {
            $n = $Matches[1]
            if (-not $seen.Add($n)) { [void]$dups.Add($n) }
        }
    }
    if ($dups.Count -gt 0) {
        Report 'DUP-FIELD' ($f.FullName + '  declared twice: ' + (($dups | Sort-Object) -join ', '))
    }
}

# --- 5. duplicate method signatures (opt-in, see -CheckDuplicateMethods) --------
if ($CheckDuplicateMethods) {
    $methodPattern = '^\s*(?:public|protected|private)\s+(?:static\s+)?(?:final\s+)?[\w<>\[\],.\?\s]+\s+([a-zA-Z]\w*)\s*\(([^)]*)\)\s*\{'
    foreach ($f in $files) {
        $seenByDepth = @{}
        $dups = New-Object 'System.Collections.Generic.HashSet[string]'
        $depth = 0
        foreach ($line in [System.IO.File]::ReadLines($f.FullName)) {
            if ($line -match $methodPattern) {
                $paramTypes = ($Matches[2] -split ',') | ForEach-Object {
                    $p = $_.Trim() -replace '\s+\w+\s*$', ''
                    if ([string]::IsNullOrWhiteSpace($p)) { '' } else { $p }
                }
                $sig = $Matches[1] + '(' + ($paramTypes -join ',') + ')'
                if (-not $seenByDepth.ContainsKey($depth)) {
                    $seenByDepth[$depth] = New-Object 'System.Collections.Generic.HashSet[string]'
                }
                if (-not $seenByDepth[$depth].Add($sig)) { [void]$dups.Add($sig) }
            }
            foreach ($ch in $line.ToCharArray()) {
                if ($ch -eq '{') { $depth++ } elseif ($ch -eq '}') { $depth-- }
            }
        }
        if ($dups.Count -gt 0) {
            ReportWarning 'DUP-METHOD?' ($f.FullName + '  same signature twice: ' + (($dups | Sort-Object) -join ' | ') + '  (may be two nested classes)')
        }
    }
}

# --- summary --------------------------------------------------------------------
$problemCount = $diag.Problems.Count
$warningCount = $diag.Warnings.Count
Write-Host ''
if ($problemCount -eq 0) {
    Write-Host 'CHECK OK: no brace / BOM / import / duplicate-field problems.' -ForegroundColor Green
    if ($warningCount -gt 0) {
        Write-Host ("({0} advisory warning(s) above)" -f $warningCount) -ForegroundColor DarkGray
    }
    exit 0
}
Write-Host ("CHECK FAILED: {0} problem(s) (BOM={1}). Fix them before compiling." -f $problemCount, $bomCount) -ForegroundColor Red
Write-Host 'NOTE: "IMPORT?" can be a false positive for nested types of OTHER classes' -ForegroundColor DarkGray
Write-Host '      (e.g. Other.Nested) -- verify by hand before adding an import.' -ForegroundColor DarkGray
exit 1
