# xq-svc-exercise

Exercise log book microservice and PostgreSQL database.

Initial scope is single-user exercise logging. A log contains an exercise name and one or more
sets. Each set records weight in kilograms and repetitions. Set volume is calculated by the
service as `weightKg * reps`.
