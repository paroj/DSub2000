### 🟡 Changes recommended

Fresh folders still share artwork, while several deletion paths can orphan per-track sidecars.

*Once you've addressed the issues Copilot identified, you can request another Copilot review.*

<details>
<summary>Pull request overview</summary>

Attempts to support track-specific embedded artwork in mixed-artist folders.

**Changes:**
- Detects artist/folder mismatches and creates per-track artwork sidecars.
- Deletes sidecar artwork during recursive song deletion.
</details>

<details>
<summary>File summaries</summary>

| File | Description |
| ---- | ----------- |
| `FileUtil.java` | Adds per-track artwork paths and cleanup logic. |
</details>

<details>
<summary>Review details</summary>

### Suppressed comments (3)

**app/src/main/java/github/paroj/dsub2000/util/FileUtil.java:299**
* `getSongFile(context, entry)` returns the unstaged filename, so globally removing `.complete` and `.partial` here only alters legitimate track titles. For example, `mix.partial.take.mp3` collides with `mix.take.mp3` at `mix.take.jpg`, allowing one track's art to overwrite the other's.
```
				coverPath = coverPath.replace(".complete", "").replace(".partial", "");
```
**app/src/main/java/github/paroj/dsub2000/util/FileUtil.java:637**
* Removing stage markers from the entire basename maps legitimate titles such as `mix.partial.take.mp3` to another track's sidecar and can delete the wrong artwork. Because a title may also legitimately end in `.partial`/`.complete`, this path-only code cannot reliably distinguish a staging suffix; sidecar deletion should use the entry-derived artwork path rather than rewriting arbitrary substrings.
```
					String coverPath = path.substring(0, index).replace(".complete", "").replace(".partial", "") + ".jpg";
```
**app/src/main/java/github/paroj/dsub2000/util/FileUtil.java:640**
* The return value from deleting the sidecar is ignored, so `recursiveDelete` can report success after deleting the song while leaving its artwork behind. This method already propagates failures for every other child deletion; do the same here.
```
					if (coverFile.exists()) {
						coverFile.delete();
```

- **Files reviewed:** 1/1 changed files
- **Comments generated:** 2
- **Review effort level:** Balanced
</details>

---

💡 <a href="/paroj/DSub2000/new/edge?filename=.github/skills/code-review/SKILL.md" class="Link--inTextBlock" target="_blank" rel="noopener noreferrer">Add a `code-review` agent skill</a> or configure MCP servers for context-aware, tailored reviews. <a href="https://docs.github.com/copilot/how-tos/use-copilot-agents/request-a-code-review/use-code-review?tool=webui#mcp-servers-and-agent-skills" class="Link--inTextBlock" target="_blank" rel="noopener noreferrer">Learn more in the docs.</a>
