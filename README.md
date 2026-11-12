<div align="center">

<a href="https://git.io/typing-svg"><img src="https://readme-typing-svg.demolab.com?font=Manrope&weight=700&size=38&pause=1000&color=EB2544&background=FFFFFF00&center=true&vCenter=true&width=700&lines=Punish+Addon" alt="Punish Addon"/></a>

<p>Наказания с настраиваемой анимацией для React</p>

<p>
  <a href="https://react-ac.space"><img src="https://img.shields.io/badge/Website-EB2544?style=plastic&logo=googlechrome&logoColor=white" alt="Website"/></a>
  <a href="https://t.me/react_ac"><img src="https://img.shields.io/badge/Telegram-26A5E4?style=plastic&logo=telegram&logoColor=white" alt="Telegram"/></a>
  <a href="https://t.me/react_ac_support"><img src="https://img.shields.io/badge/Support-555555?style=plastic&logo=telegram&logoColor=white" alt="Support"/></a>
</p>

<p>
  <a href="https://github.com/react-minecraft/punish-addon/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/react-minecraft/punish-addon/build.yml?style=plastic&logo=github&logoColor=white&branch=master&label=build" alt="Build"/></a>
  <a href="https://github.com/react-minecraft/punish-addon/releases/tag/nightly"><img src="https://img.shields.io/badge/Nightly-EB2544?style=plastic&logo=github&logoColor=white" alt="Nightly"/></a>
  <a href="https://github.com/react-minecraft/punish-addon/blob/master/LICENSE"><img src="https://img.shields.io/github/license/react-minecraft/punish-addon?style=plastic&logo=opensourceinitiative&logoColor=white" alt="License"/></a>
</p>

</div>

## Команда

`/punish <тип> [анимация] <игроки> <причина>` требует `react.punish.<тип>`. Вместо имени игрока можно использовать селектор. Аргумент анимации указывается, если у типа включён `animation.need-to-select-in-command`.

Анимация запускается на потоке игрока. Если игрок выходит до её завершения, наказание применяется при его следующем входе, пока аддон остаётся загружен.

## Настройки

Название команды задаётся в `settings.command` файла `main-config.yml`. Доступные типы и их действия задаются в `types`. `/react reload` перечитывает настройки и пересобирает команду.

```yaml
types:
  kick:
    animation:
      need-to-select-in-command: true
      selected: holyworld
    actions:
      - '[console] kick {player.name} {reason}'
```

Доступны `{player.name}`, `{player.uuid}`, `{reason}` и прежний `{context.input-after-type}`. Параметры встроенной анимации лежат в `animations/holyworld.yml`.


## Установка

1. Возьмите `punish-addon-*.jar` со страницы [Nightly](https://github.com/react-minecraft/punish-addon/releases/tag/nightly) или соберите его из исходников.
2. Положите JAR в `plugins/React/addons/`. Нужен React с API 2.
3. Перезапустите сервер или выполните `/react reboot addons`. Настройки появятся в `plugins/React/addons/ReactPunishAddon/`.

React также может установить отсутствующий аддон из стабильного канала, когда доступен подписанный релиз для API 2.


## Сборка

Для сборки используйте JDK 21. Выполните `./gradlew build` или `.\gradlew.bat build` на Windows.

Готовый `punish-addon-*.jar` лежит в `common/build/libs/`.


## Лицензия

[MIT](LICENSE).
