; GoalMaker per-user installer (Inno Setup 6), from the CodePrint .NET profile.
; Built by build-installer.ps1, which passes the version and the publish folder.

#ifndef MyAppVersion
  #error MyAppVersion must be supplied by build-installer.ps1
#endif
#ifndef PublishDir
  #error PublishDir must be supplied by build-installer.ps1
#endif
#ifndef MyVersionInfoVersion
  #error MyVersionInfoVersion must be supplied by build-installer.ps1
#endif

; A dev installer (build-installer.ps1 -Dev) installs side by side with the release.
#ifdef DevBuild
  #define MyAppName "GoalMaker Dev"
  #define MyAppId "{{891D7456-7592-58C0-9CA8-9CF02297C384}"
  #define MyRunValue "GoalMaker-dev"
  #define MyAppUserModelId "GoalMaker.Dev"
#else
  #define MyAppName "GoalMaker"
  #define MyAppId "{{48A87E5D-3B3D-5624-859B-642F3C4ADBB8}"
  #define MyRunValue "GoalMaker"
  #define MyAppUserModelId "GoalMaker"
#endif
#define MyAppPublisher "Lukáš Krejčí"
#define MyAppExeName "GoalMaker.exe"

[Setup]
; Never change AppId: it is how Windows and later installers recognize an existing install.
AppId={#MyAppId}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DisableProgramGroupPage=yes
DisableDirPage=auto
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=dist
OutputBaseFilename=GoalMaker-{#MyAppVersion}-setup
SetupIconFile=..\src\GoalMaker.App\Assets\GoalMaker.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
UninstallDisplayName={#MyAppName}
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
; The in-app updater runs this silently; Restart Manager closes the running app first.
CloseApplications=yes
CloseApplicationsFilter={#MyAppExeName}
RestartApplications=no
VersionInfoVersion={#MyVersionInfoVersion}
VersionInfoCompany={#MyAppPublisher}
VersionInfoProductName={#MyAppName}
LicenseFile=..\..\LICENSE.md

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "startup"; Description: "Start GoalMaker in the tray when I sign in to Windows (needed for reminders)"; GroupDescription: "Startup:"
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Additional shortcuts:"; Flags: unchecked

[Files]
Source: "{#PublishDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; Tasks: desktopicon

[Registry]
; Per-user sign-in start. Startup Profiles can manage this instead (Settings, Startup).
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "{#MyRunValue}"; ValueData: """{app}\{#MyAppExeName}"" --tray"; Tasks: startup; Flags: uninsdeletevalue
; The app registers its AppUserModelID for reminder toasts at every start (ADR 0009); uninstalling removes it.
Root: HKCU; Subkey: "Software\Classes\AppUserModelId\{#MyAppUserModelId}"; Flags: uninsdeletekey dontcreatekey

[Run]
; Not skipifsilent: after a silent in-app update, GoalMaker starts again by itself.
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#MyAppName}}"; Flags: nowait postinstall

; User data (%LOCALAPPDATA%\GoalMaker) is never removed by the uninstaller. Supabase holds the
; source of truth; the local folder only has the session, settings and caches.

[Code]
// GoalMaker is published framework-dependent (ADR 0004): check the .NET 10 Desktop Runtime.
function DesktopRuntimeInstalled(): Boolean;
var
  FindRec: TFindRec;
begin
  Result := FindFirst(ExpandConstant('{commonpf64}\dotnet\shared\Microsoft.WindowsDesktop.App\10.*'), FindRec);
  if Result then
    FindClose(FindRec)
  else
  begin
    Result := FindFirst(ExpandConstant('{localappdata}\Microsoft\dotnet\shared\Microsoft.WindowsDesktop.App\10.*'), FindRec);
    if Result then
      FindClose(FindRec);
  end;
end;

function InitializeSetup(): Boolean;
var
  ErrorCode: Integer;
begin
  Result := DesktopRuntimeInstalled();
  if (not Result) and (not WizardSilent()) then
  begin
    if MsgBox('GoalMaker needs the .NET 10 Desktop Runtime (x64), which is not installed.' + #13#10#13#10 +
              'Open the download page now? Run this installer again afterwards.', mbConfirmation, MB_YESNO) = IDYES then
      ShellExec('open', 'https://dotnet.microsoft.com/download/dotnet/10.0', '', '', SW_SHOWNORMAL, ewNoWait, ErrorCode);
  end;
end;
