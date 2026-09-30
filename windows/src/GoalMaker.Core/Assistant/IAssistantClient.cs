namespace GoalMaker.Core.Assistant;

/// <summary>
/// The quick chat's assistant (spec, "Quick chat (M7)"): the whole thread so far goes to the server,
/// which runs the model and its tools as the signed-in owner and answers in plain text. The thread is
/// never stored; each call carries it all. Only a cancellation throws; every other failure is a
/// <see cref="AssistantReply.Failure"/>.
/// </summary>
public interface IAssistantClient
{
    /// <param name="messages">The thread so far, oldest first; the last one is the owner's.</param>
    Task<AssistantReply> SendAsync(IReadOnlyList<AssistantMessage> messages, CancellationToken cancellationToken = default);
}
