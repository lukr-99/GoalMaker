namespace GoalMaker.App.Tests;

/// <summary>Runs a test on its own STA thread, as WPF needs, and rethrows what failed there.</summary>
internal static class StaThread
{
    public static void Run(Action test)
    {
        Exception? failure = null;
        var thread = new Thread(() =>
        {
            try
            {
                test();
            }
            catch (Exception exception)
            {
                failure = exception;
            }
        });
        thread.SetApartmentState(ApartmentState.STA);
        thread.Start();
        thread.Join();
        if (failure is not null)
        {
            throw new InvalidOperationException("The test failed on its STA thread", failure);
        }
    }
}
