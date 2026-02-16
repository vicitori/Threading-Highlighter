# Threading Highlighter

IntelliJ Platform плагин для визуализации threading контрактов в коде.

## Модули

- **common** - общие классы и модели данных (маркеры, trace записи, конфигурация)
- **agent** - Java agent для инструментации методов проверки потоков в runtime
- **plugin** - IntelliJ Platform плагин с аннотаторами и UI
- **examples** - примеры использования с тестовыми actions

## Запуск

### 1. Сборка и установка плагина в основную IDE

```bash
./gradlew :plugin:buildPlugin
```

После сборки плагин находится в `plugin/build/distributions/`.
Установить плагин в основную IDE через Settings → Plugins → Install Plugin from Disk.

### 2. Сборка и запуск примера

```bash
./gradlew :examples:runIde
```

Gradle автоматически настроит VM options для подключения агента.
Открывается тестовая IDE, в которой работает пользовательский плагин, инструментируемый агентом.

### 3. Выполнение тестовых actions

В запущенной тестовой IDE нажать комбинации клавиш:
- **Shift+X** - Slow Operation marker
- **Shift+E** - EDT marker
- **Shift+N** - Non-EDT marker
- **Shift+Y** - Chained markers

### 4. Просмотр результатов

Закрыть тестовую IDE. В рабочей IDE:

**Tools → Threading Highlighter → Reload Threading Traces**

В коде примера (examples/src/main/kotlin) появятся gutter иконки:
- **EDT marker** - код выполнялся на EDT
- **Non-EDT marker** - код выполнялся не на EDT
- **Slow operation marker** - выполнялась медленная операция

Клик по иконке показывает детали: количество вызовов, имена потоков, stack traces.

**Tools → Threading Highlighter → Show Trace Summary** - показать общую статистику по всем записям

## Trace файлы

Агент записывает данные в `~/.ij-threading-highlighter/`.
Каждому маркеру соответствует свой файл с инструкциями.

В файлах хранятся уникальные инструкции с последним временем их выполнения.
Данные автоматически сохраняются на диск раз в 15 минут работы плагина (фича не проверялась из-за простоты примеров).
