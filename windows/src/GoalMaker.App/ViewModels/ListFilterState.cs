using GoalMaker.Core.Planning;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// The one filter Today, Tomorrow and the Inbox share (docs/lists.md), so it stays when the owner
/// switches lists. Choosing the area or tag already chosen clears it again.
/// </summary>
public sealed class ListFilterState
{
    public ListFilter Current { get; private set; } = ListFilter.None;

    public event EventHandler? Changed;

    public void ToggleArea(string areaId) => Set(Current with { AreaId = Current.AreaId == areaId ? null : areaId });

    public void ToggleTag(string tagId) => Set(Current with { TagId = Current.TagId == tagId ? null : tagId });

    public void Clear() => Set(ListFilter.None);

    /// <summary>Lets go of an area or tag that no longer exists, so a deleted one doesn't hide everything.</summary>
    public void Forget(IReadOnlyCollection<string> areaIds, IReadOnlyCollection<string> tagIds) => Set(new ListFilter(
        Current.AreaId is { } area && areaIds.Contains(area) ? area : null,
        Current.TagId is { } tag && tagIds.Contains(tag) ? tag : null));

    private void Set(ListFilter filter)
    {
        if (filter == Current)
        {
            return;
        }

        Current = filter;
        Changed?.Invoke(this, EventArgs.Empty);
    }
}
