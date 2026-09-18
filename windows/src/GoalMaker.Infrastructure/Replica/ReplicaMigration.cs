namespace GoalMaker.Infrastructure.Replica;

/// <summary>One file of replica/migrations: its number, file name, SQL and SHA-256 of the exact bytes.</summary>
public sealed record ReplicaMigration(int Number, string Name, string Sql, string Checksum);
