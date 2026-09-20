namespace GoalMaker.Core.Backup;

/// <summary>
/// The folder the weekly export writes into, as the rules see it: is it there, write a file, list
/// the exports already in it, remove one. The real one is on disk; the tests use their own.
/// </summary>
public interface IBackupFolder
{
    bool Exists(string folder);

    /// <summary>Writes the file, replacing one of the same name. False when it could not be written.</summary>
    bool Write(string folder, string name, string text);

    /// <summary>The GoalMaker exports already in the folder, by name.</summary>
    IReadOnlyList<string> Exports(string folder);

    void Remove(string folder, string name);
}
