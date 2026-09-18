namespace GoalMaker.Core.Updates;

/// <summary>Starts a verified installer. The app exits afterwards so the installer can replace it.</summary>
public interface IUpdateInstaller
{
    void Launch(string localPath);
}
