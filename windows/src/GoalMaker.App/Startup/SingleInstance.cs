using System.IO;
using System.IO.Pipes;
using System.Text;

namespace GoalMaker.App.Startup;

/// <summary>
/// One GoalMaker per Windows user and build kind. A second launch hands its arguments to the running
/// instance over a named pipe that only the current user can open, then exits.
/// </summary>
public sealed class SingleInstance : IDisposable
{
    private readonly Mutex mutex;
    private readonly string pipeName;
    private readonly CancellationTokenSource stopping = new();

    private SingleInstance(Mutex mutex, string pipeName)
    {
        this.mutex = mutex;
        this.pipeName = pipeName;
    }

    /// <summary>Returns the instance lock, or null when another instance already holds it.</summary>
    public static SingleInstance? TryAcquire(string name)
    {
        var mutex = new Mutex(initiallyOwned: true, $@"Local\{name}.Instance", out var created);
        if (created)
        {
            return new SingleInstance(mutex, PipeName(name));
        }

        mutex.Dispose();
        return null;
    }

    /// <summary>Sends <paramref name="arguments"/> to the running instance. False if it can't be reached.</summary>
    public static bool Forward(string name, IReadOnlyList<string> arguments)
    {
        try
        {
            using var client = new NamedPipeClientStream(".", PipeName(name), PipeDirection.Out, PipeOptions.CurrentUserOnly);
            client.Connect(TimeSpan.FromSeconds(3));
            using var writer = new StreamWriter(client, Encoding.UTF8);
            writer.Write(string.Join('\n', arguments));
            return true;
        }
        catch (Exception error) when (error is IOException or TimeoutException or UnauthorizedAccessException)
        {
            return false;
        }
    }

    /// <summary>Calls <paramref name="onArguments"/> (on a background thread) for every forwarded launch.</summary>
    public void Listen(Action<IReadOnlyList<string>> onArguments)
    {
        _ = Task.Run(async () =>
        {
            while (!stopping.IsCancellationRequested)
            {
                try
                {
                    await using var server = new NamedPipeServerStream(
                        pipeName,
                        PipeDirection.In,
                        1,
                        PipeTransmissionMode.Byte,
                        PipeOptions.Asynchronous | PipeOptions.CurrentUserOnly);
                    await server.WaitForConnectionAsync(stopping.Token).ConfigureAwait(false);
                    using var reader = new StreamReader(server, Encoding.UTF8);
                    var text = await reader.ReadToEndAsync(stopping.Token).ConfigureAwait(false);
                    onArguments(text.Split('\n', StringSplitOptions.RemoveEmptyEntries));
                }
                catch (OperationCanceledException)
                {
                    return;
                }
                catch (IOException)
                {
                    // A client that disconnects early is not an error; keep listening.
                }
            }
        });
    }

    public void Dispose()
    {
        stopping.Cancel();
        stopping.Dispose();
        mutex.ReleaseMutex();
        mutex.Dispose();
    }

    private static string PipeName(string name) => $"{name}.{Environment.UserName}.Launch";
}
