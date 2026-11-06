Option Explicit
Dim fs, root, shell, q
Set fs = CreateObject("Scripting.FileSystemObject")
root = fs.GetParentFolderName(WScript.ScriptFullName)
q = Chr(34)
Set shell = CreateObject("WScript.Shell")
shell.Run q & root & "\bootstrap\runtime\bin\javaw.exe" & q & " -cp " & q & root & "\bootstrap\app\*" & q & " be.cookies.studio.core.update.Bootstrap " & q & root & q, 0, False
