# 命令系统自测脚本（由 AI 编写，作者未逐条核对）
#
# 用法（在项目根目录）：
#   powershell -ExecutionPolicy Bypass -File .\test-command-system.ps1
#   或
#   pwsh -File .\test-command-system.ps1
#
# 做了三件事：
#   1. 把 src 下全部 .java 编译到 out\cmdtest
#   2. 运行 cn.gfhnv.debug_tools.TestCommandSystem
#   3. 把编译/运行输出显示出来，并按退出码返回结果

$ErrorActionPreference = 'Stop'

# 切到脚本所在目录（也就是项目根目录）
Set-Location -Path $PSScriptRoot

Write-Host '========== 1/3 检查环境 ==========' -ForegroundColor Cyan

$javac = Get-Command javac -ErrorAction SilentlyContinue
$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $javac) { Write-Host 'ERROR: 找不到 javac，请确认 JDK 已在 PATH 中' -ForegroundColor Red; exit 1 }
if (-not $java) { Write-Host 'ERROR: 找不到 java，请确认 JDK 已在 PATH 中' -ForegroundColor Red; exit 1 }
Write-Host "javac: $($javac.Source)"
Write-Host "java : $($java.Source)"

$jsonLib = Join-Path $PWD 'lib\json-20231013.jar'
if (-not (Test-Path $jsonLib)) { Write-Host "ERROR: 找不到 $jsonLib" -ForegroundColor Red; exit 1 }

$outDir = Join-Path $PWD 'out\cmdtest'
if (Test-Path $outDir) { Remove-Item -Recurse -Force $outDir }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

Write-Host ''
Write-Host '========== 2/3 编译全部源码 ==========' -ForegroundColor Cyan

$sources = Get-ChildItem -Path (Join-Path $PWD 'src') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
Write-Host "源码文件数：$($sources.Count)"

$compileLog = Join-Path $outDir 'compile.log'
# 用数组传参：PowerShell 5 会把裸写的 -Dfile.encoding=UTF-8 在 '.' 处拆开，
# 导致 java 把 ".encoding=UTF-8" 当成主类名（"找不到或无法加载主类 .encoding=UTF-8"）。
$javacArgs = @(
    '-encoding', 'UTF-8',
    '-nowarn',
    '-d', $outDir,
    '-classpath', $jsonLib
) + $sources
& javac @javacArgs 2>&1 |
    ForEach-Object { $_.ToString() } | Tee-Object -FilePath $compileLog
$compileExit = $LASTEXITCODE
if ($compileExit -ne 0) {
    Write-Host ''
    Write-Host "编译失败（exit=$compileExit），完整日志：$compileLog" -ForegroundColor Red
    exit $compileExit
}
Write-Host '编译成功' -ForegroundColor Green

Write-Host ''
Write-Host '========== 3/3 运行命令系统自测 ==========' -ForegroundColor Cyan

# Java 侧会把标准输出切成 UTF-8，这里把控制台代码页也切到 65001，避免中文乱码
try { chcp 65001 | Out-Null } catch { }

$classpath = "$outDir;$jsonLib"
$javaArgs = @(
    '-Dfile.encoding=UTF-8',
    '-cp', $classpath,
    'cn.gfhnv.debug_tools.TestCommandSystem'
)
& java @javaArgs
$runExit = $LASTEXITCODE

Write-Host ''
if ($runExit -eq 0) {
    Write-Host '自测通过（exit=0）' -ForegroundColor Green
} else {
    Write-Host "自测存在失败项（exit=$runExit），请把上面的 [FAIL] 行贴给 AI" -ForegroundColor Yellow
}
exit $runExit
