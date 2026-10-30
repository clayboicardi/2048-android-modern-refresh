# Release keep rules. Intentionally empty: kotlinx-serialization ships its own R8 rules,
# and no @Serializable class has a named companion object (D-134). Add a rule only with a
# release-only failure that proves it is needed.
