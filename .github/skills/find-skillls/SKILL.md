---
name: find-skillls
description: Use this skill to quickly discover available project skills and explain how to invoke them in this repository.
---

# Find Skills

Use this skill when users ask:
- What skills are available now
- Which skill should be used for a task
- How to invoke a skill in this repository

## How to discover skills

1. Check `.github/skills/` for repository-level skills.
2. List each skill folder name as the invocation name.
3. Read `SKILL.md` frontmatter (`name`, `description`) for usage guidance.

## How to invoke

Invoke skills by exact name through the skill tool, for example:
- `wilderness-project`
- `wilderness-style`
- `find-skillls`

## Expected output format

When answering a user request about skills:

1. Show available skill names.
2. Give one-line intent for each skill.
3. Recommend the best one for the user’s current task.
