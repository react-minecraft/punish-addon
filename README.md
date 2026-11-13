<div align="center">

<a href="https://git.io/typing-svg"><img src="https://readme-typing-svg.demolab.com?font=Manrope&weight=700&size=38&pause=1000&color=EB2544&background=FFFFFF00&center=true&vCenter=true&width=700&lines=Punish+Addon" alt="Punish Addon Typing SVG"/></a>

<p>Наказания с анимацией для React</p>

<p>
  <a href="https://github.com/react-minecraft/punish-addon/blob/master/LICENSE">
    <img src="https://img.shields.io/github/license/react-minecraft/punish-addon?style=plastic&logo=opensourceinitiative&logoColor=white" alt="License">
  </a>
  <a href="https://github.com/react-minecraft/punish-addon/actions/workflows/build.yml">
    <img src="https://img.shields.io/github/actions/workflow/status/react-minecraft/punish-addon/build.yml?branch=master&style=plastic&logo=github&logoColor=white" alt="Build">
  </a>
</p>

<p>
  <a href="https://react-ac.space"><img src="https://img.shields.io/badge/Website-EB2544?style=plastic&logo=googlechrome&logoColor=white" alt="Website"/></a>
  <a href="https://t.me/react_ac"><img src="https://img.shields.io/badge/Telegram-26A5E4?style=plastic&logo=telegram&logoColor=white" alt="Telegram"/></a>
  <a href="https://t.me/react_ac_support"><img src="https://img.shields.io/badge/Support-555555?style=plastic&logo=telegram&logoColor=white" alt="Support"/></a>
</p>

</div>

---

## Что это?

- Аддон для плагина [React](https://github.com/react-minecraft/plugin): выдаёт наказания командой и перед этим
  проигрывает над игроком анимацию из частиц.
- Типы наказаний (`kick`, `ban`, `mute`) и их команды задаются в `main-config.yml`.
- Анимации лежат в `animations/`, встроенная — `holyworld`.

## Команды

| Команда | Право |
|---|---|
| `/punish <тип> [анимация] <игроки> <причина>` | `react.punish.<тип>` |

- Команда своя, на Cloud API плагина: её имя задаёт `settings.command`, плагин снимает её вместе с аддоном.
- `<игроки>` — ник или селектор (`@a`, `@p`).
- `[анимация]` спрашивается, только если у типа стоит `need-to-select-in-command: true`.
- `/react reload` перечитывает конфиги и пересобирает команду.
- Если игрок вышел во время анимации, действия выполнятся, когда он зайдёт, пока аддон загружен.

## Конфиг

```yaml
settings:
  command: punish   # /punish; старый ключ argument-literal тоже читается

types:
  kick:
    animation:
      need-to-select-in-command: true
      selected: holyworld
    actions:
      - '[console] kick {player.name} {reason}'
```

В действиях доступны `{player.name}`, `{player.uuid}` и `{reason}` (старое `{context.input-after-type}` тоже работает).

## Установка

1. Соберите аддон или скачайте jar из [GitHub Nightly](https://github.com/react-minecraft/punish-addon/releases/tag/nightly). Нужен React с API 2.
2. Положите `punish-addon-<версия>.jar` в `plugins/React/addons/`.
3. Перезапустите сервер или выполните `/react reboot addons`. Конфиг появится в `plugins/React/addons/ReactPunishAddon/`.

## Сборка

Для сборки нужен JDK 21.

```bash
./gradlew build
```

Готовый jar лежит в `common/build/libs/`.

## Лицензия

См. [LICENSE](LICENSE).
