namespace GoalMaker.Core.Assistant;

/// <summary>What the assistant said back: an answer, or a problem the chat can explain in plain words.</summary>
public abstract record AssistantReply
{
    private AssistantReply()
    {
    }

    public sealed record Answer(string Text) : AssistantReply;

    /// <param name="Detail">The server's own words or the error, for diagnostics; never shown as is.</param>
    public sealed record Failure(AssistantProblem Problem, string? Detail = null) : AssistantReply;
}
