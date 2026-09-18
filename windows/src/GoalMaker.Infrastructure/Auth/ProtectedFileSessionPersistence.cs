using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Supabase.Gotrue;
using Supabase.Gotrue.Interfaces;

namespace GoalMaker.Infrastructure.Auth;

/// <summary>
/// Keeps the Supabase session in one file encrypted with DPAPI for the current Windows user, so
/// another account on the PC (or a copied file) can't reuse it.
/// </summary>
public sealed class ProtectedFileSessionPersistence(string path) : IGotrueSessionPersistence<Session>
{
    private static readonly byte[] Entropy = Encoding.UTF8.GetBytes("GoalMaker.Session.v1");

    public void SaveSession(Session session)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var json = JsonSerializer.SerializeToUtf8Bytes(session);
        File.WriteAllBytes(path, ProtectedData.Protect(json, Entropy, DataProtectionScope.CurrentUser));
    }

    public void DestroySession()
    {
        if (File.Exists(path))
        {
            File.Delete(path);
        }
    }

    public Session? LoadSession()
    {
        if (!File.Exists(path))
        {
            return null;
        }

        try
        {
            var json = ProtectedData.Unprotect(File.ReadAllBytes(path), Entropy, DataProtectionScope.CurrentUser);
            return JsonSerializer.Deserialize<Session>(json);
        }
        catch (Exception error) when (error is CryptographicException or JsonException)
        {
            // Unreadable (another user, corrupted): treat as signed out.
            DestroySession();
            return null;
        }
    }

    public Task SaveSessionAsync(Session session, CancellationToken cancellationToken)
    {
        SaveSession(session);
        return Task.CompletedTask;
    }

    public Task DestroySessionAsync(CancellationToken cancellationToken)
    {
        DestroySession();
        return Task.CompletedTask;
    }

    public Task<Session?> LoadSessionAsync(CancellationToken cancellationToken) => Task.FromResult(LoadSession());
}
