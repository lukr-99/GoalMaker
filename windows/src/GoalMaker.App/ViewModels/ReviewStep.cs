namespace GoalMaker.App.ViewModels;

/// <summary>Where the guided review is (docs/reviews.md). The Letter comes first, only when there is one (docs/letter.md).</summary>
public enum ReviewStep
{
    Letter,
    LookBack,
    Tasks,
    Reflect,
    Rate,
    Goals,
    Done,
}
