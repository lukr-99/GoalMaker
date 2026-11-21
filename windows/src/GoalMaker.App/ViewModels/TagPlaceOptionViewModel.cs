using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>One choice in a tag's Belongs to picker: everything (<see cref="Place"/> null), an area or a project.</summary>
public sealed record TagPlaceOptionViewModel(TagPlace? Place, string Label);
