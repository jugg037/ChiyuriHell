#ifndef AppVersion
  #define AppVersion "0.1.0"
#endif

#define AppName "Chiyuri AI"
#define AppExe "ChiyuriAI.exe"
#define AppPublisher "Chiyuri AI"

[Setup]
AppId={{7E2B4E1C-9B71-4B28-9A7E-6D7C9B1F2A40}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={localappdata}\Programs\ChiyuriAI
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
OutputDir=..\dist
OutputBaseFilename=ChiyuriAI-Setup
SetupLogging=yes
WizardStyle=modern
UninstallDisplayIcon={app}\{#AppExe}
CloseApplications=yes
RestartApplications=no
Compression=lzma2
SolidCompression=yes

[Tasks]
Name: "desktopicon"; Description: "Создать ярлык на рабочем столе"; GroupDescription: "Дополнительные ярлыки:"; Flags: unchecked

[Files]
Source: "..\dist\ChiyuriAI\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\Chiyuri AI"; Filename: "{app}\{#AppExe}"; WorkingDir: "{app}"
Name: "{autodesktop}\Chiyuri AI"; Filename: "{app}\{#AppExe}"; WorkingDir: "{app}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExe}"; Description: "Запустить Chiyuri AI"; Flags: postinstall nowait skipifsilent
