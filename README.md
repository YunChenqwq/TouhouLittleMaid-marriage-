# Touhou Little Maid: XinQiTongMian (NeoForge 1.21.1)

Addon mod for Touhou Little Maid focused on marriage, romance sleep events, pregnancy/childbirth, child maid growth, and child maid work systems.

![Mod Preview](./????.png)

- GitHub: https://github.com/YunChenqwq/TouhouLittleMaid-marriage-.git
- Minecraft: 1.21.1
- Loader: NeoForge 21.1.x
- Java: 21
- Dependency: Touhou Little Maid (NeoForge 1.21.1 line)

## Main Features

### 1) Proposal / Marriage / Sleep
- Use `proposal_ring` to propose.
- Use `yes_pillow` after marriage to trigger romance sleep flow.
- Sleep flow may cause pregnancy; follow-up flow can lead to childbirth.

### 2) Child Maid
- Child growth stages with promotion to adult maid.
- Model inheritance support (including YSM-related data sync).
- Soul-sign recall path attempts to keep child state.

### 3) Child Work (Study / Explore)
- Work modes are switched from maid task panel.
- Input consumption is main-hand first.
- Unstackable inputs for Alchemy/Tactics can fallback to backpack.
- Near exploration now uses **Stick** and returns **2 items** per run.

### 4) Favorability
- Child has default favorability baseline.
- Work consumes favorability; idle rest can recover favorability.
- Low favorability can lock actions until recovered.

## Recent Updates
- Fixed child maid hand/armor capability registration mismatch.
- Added wake dialogue delay to reduce post-sleep pose desync.
- Added pregnancy dialogue and adjusted post-romance line.
- Reworked input consumption logic (main hand priority).
- Fixed interrupted-task timer so standing up no longer instant-completes task.

## Build

```powershell
./gradlew.bat build
./gradlew.bat runClient
```

If Java is missing, set `JAVA_HOME` to JDK 21 first.

## Quick Test Commands

```mcfunction
/give @p maidmarriage:proposal_ring
/give @p maidmarriage:yes_pillow
/give @p maidmarriage:longing_tester
```
