using System.Collections.ObjectModel;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// Brings a shown collection in line with what it should hold by removing, moving and inserting only
/// what differs, item by reference (GM-121). Clearing and refilling would rebuild every row the list
/// draws, and lose the keyboard and the scroll position with them; an unchanged list raises no event.
/// A <see cref="ShownCollection{T}"/> that changes almost wholly is replaced in one Reset instead.
/// </summary>
public static class CollectionSync
{
    public static void SyncTo<T>(this ObservableCollection<T> shown, IReadOnlyList<T> wanted)
        where T : class
    {
        var keep = new HashSet<T>(wanted, ReferenceEqualityComparer.Instance);

        // When most of it is new (another project's board), one Reset is cheaper than a change per item.
        if (shown is ShownCollection<T> replaceable && shown.Count + wanted.Count > 16 && 2 * shown.Count(keep.Contains) < wanted.Count)
        {
            replaceable.ReplaceAll(wanted);
            return;
        }

        for (var i = shown.Count - 1; i >= 0; i--)
        {
            if (!keep.Contains(shown[i]))
            {
                shown.RemoveAt(i);
            }
        }

        for (var i = 0; i < wanted.Count; i++)
        {
            var item = wanted[i];
            if (i < shown.Count && ReferenceEquals(shown[i], item))
            {
                continue;
            }

            var at = IndexOf(shown, item, i + 1);
            if (at >= 0)
            {
                shown.Move(at, i);
            }
            else
            {
                shown.Insert(i, item);
            }
        }

        while (shown.Count > wanted.Count)
        {
            shown.RemoveAt(shown.Count - 1);
        }
    }

    private static int IndexOf<T>(ObservableCollection<T> shown, T item, int from)
        where T : class
    {
        for (var i = from; i < shown.Count; i++)
        {
            if (ReferenceEquals(shown[i], item))
            {
                return i;
            }
        }

        return -1;
    }
}
