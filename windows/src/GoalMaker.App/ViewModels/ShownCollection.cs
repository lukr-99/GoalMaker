using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.ComponentModel;

namespace GoalMaker.App.ViewModels;

/// <summary>
/// A shown collection that can take a whole new content in one change (GM-121): a list told of each of
/// four hundred removals one by one, when another project's board comes in, takes far longer than a
/// list told once that everything changed.
/// </summary>
public sealed class ShownCollection<T> : ObservableCollection<T>
{
    /// <summary>Holds <paramref name="items"/> instead, with one Reset.</summary>
    public void ReplaceAll(IEnumerable<T> items)
    {
        CheckReentrancy();
        Items.Clear();
        foreach (var item in items)
        {
            Items.Add(item);
        }

        OnPropertyChanged(new PropertyChangedEventArgs(nameof(Count)));
        OnPropertyChanged(new PropertyChangedEventArgs("Item[]"));
        OnCollectionChanged(new NotifyCollectionChangedEventArgs(NotifyCollectionChangedAction.Reset));
    }
}
