# AxoHologram

Sistema de hologramas para **Paper** basado en **Display Entities**.

El plugin está orientado a hologramas de texto, ítem y bloque, con edición por comandos, páginas, formato avanzado de texto e integración opcional con FancyNPCs.

## Requisitos

- **Paper** `1.21.4+`
- **Java** `25+`

## Características

- Hologramas basados en `TextDisplay`, `ItemDisplay` y `BlockDisplay`
- Tipos soportados:
  - `TEXT`
  - `ITEM`
  - `BLOCK`
- MiniMessage soportado en texto
- Colores **HEX**
- Formato **legacy** con `&` y `§`
- PlaceholderAPI opcional
- MiniPlaceholders opcional
- Páginas múltiples por holograma
- Visibilidad por:
  - todos
  - manual
  - permiso
- Estilo configurable:
  - `billboard`
  - `scale`
  - `shadow`
  - `background`
  - `text shadow`
  - `brightness`
  - `alignment`
- Posicionamiento:
  - mover al jugador
  - mover a coordenadas
  - rotación horizontal
  - rotación vertical
  - offset base del holograma
  - offset por línea
- Integración opcional con **FancyNPCs**
  - vincular hologramas a un NPC
  - seguimiento automático de posición
- Archivos separados por holograma en:
  - `plugins/AxoHologram/holograms/<id>.yml`

## Instalación

1. Compila el proyecto:

```bash
mvn clean package
```

2. Copia el jar generado en `plugins/`
3. Inicia el servidor

El jar se genera en:

```text
target/AxoHologram-1.1.4-alpha.jar
```

## Comando base

Comando principal:

```text
/holograma
```

Comandos equivalentes:

```text
/axohologram
/aholo
/axoholo
/holo
```

## Comandos

### Gestión básica

| Comando | Descripción |
|---|---|
| `/holograma create <text\|item\|block> <id>` | Crea un holograma nuevo |
| `/holograma create <id>` | Compatibilidad legacy, crea un holograma `text` |
| `/holograma delete <id>` | Elimina un holograma |
| `/holograma list` | Lista hologramas cargados |
| `/holograma reload` | Recarga configuración y hologramas |
| `/holograma version` | Muestra la versión actual del plugin |
| `/holograma ver` | Alias de `version` |
| `/holograma teleport <id>` | Teleporta al jugador al holograma |

### Posición y movimiento

| Comando | Descripción |
|---|---|
| `/holograma movehere <id>` | Mueve el holograma a tu posición |
| `/holograma move <id>` | Alias legacy de `movehere` |
| `/holograma moveto <id> <x> <y> <z> [yaw] [pitch]` | Mueve el holograma a coordenadas |
| `/holograma rotate <id> <degrees>` | Cambia la rotación horizontal |
| `/holograma rotatepitch <id> <degrees>` | Cambia la rotación vertical |
| `/holograma offset <id> <x> <y> <z>` | Cambia el offset base del holograma |

Nota: si un holograma está vinculado a FancyNPCs, no puede moverse manualmente hasta hacer `unlink`.

### Páginas

| Comando | Descripción |
|---|---|
| `/holograma page add <id>` | Añade una página |
| `/holograma page delete <id> <page>` | Elimina una página |
| `/holograma page remove <id> <page>` | Alias legacy de `page delete` |
| `/holograma page default <id> <page>` | Define la página por defecto |
| `/holograma page set <id> <page>` | Alias legacy de `page default` |

### Líneas

| Comando | Descripción |
|---|---|
| `/holograma line add <id> <page> <type> <content>` | Añade una línea |
| `/holograma line set <id> <page> <line> <content>` | Cambia el contenido de una línea |
| `/holograma line delete <id> <page> <line>` | Elimina una línea |
| `/holograma line remove <id> <page> <line>` | Alias legacy de `line delete` |
| `/holograma line offset <id> <page> <line> <x> <y> <z>` | Cambia el offset de una línea |

Tipos de línea válidos:

```text
text
item
block
```

Ejemplos:

```text
/holograma line add bienvenida 1 text <gradient:#9D00FF:#00E5FF>Hola</gradient>
/holograma line add espada 1 item DIAMOND_SWORD
/holograma line add bloque 1 block GRASS_BLOCK
```

### Permisos de visualización

| Comando | Descripción |
|---|---|
| `/holograma permission <id> <permission>` | Define un permiso personalizado |
| `/holograma permission <id>` | Limpia el permiso personalizado y vuelve a `all` |

Permiso efectivo por defecto si usas visibilidad por permiso:

```text
axohologram.view.<id>
```

### Visibilidad y estilo

| Comando | Descripción |
|---|---|
| `/holograma viewdistance <id> <distance>` | Cambia la distancia de render |
| `/holograma viewdistance <id> default` | Vuelve a la distancia por defecto del config |
| `/holograma visibility <id> <all\|manual\|permission>` | Cambia el modo de visibilidad |
| `/holograma scale <id> <factor>` | Cambia la escala |
| `/holograma billboard <id> <center\|fixed\|vertical\|horizontal>` | Cambia el billboard |
| `/holograma shadow strength <id> <value>` | Cambia la fuerza de sombra |
| `/holograma shadow radius <id> <value>` | Cambia el radio de sombra |
| `/holograma background <id> <color\|transparent\|transparente>` | Cambia el fondo del texto |
| `/holograma textshadow <id> <true\|false>` | Activa o desactiva sombra de texto |
| `/holograma brightness <id> <block\|sky> <0-15>` | Cambia el brillo |
| `/holograma align <id> <center\|left\|right>` | Cambia la alineación del texto |

### FancyNPCs

| Comando | Descripción |
|---|---|
| `/holograma npc link <holograma> <npc>` | Vincula un holograma a un NPC |
| `/holograma npc unlink <holograma>` | Quita el vínculo |
| `/holograma npc info <holograma>` | Muestra el estado del vínculo |

## Permisos

| Permiso | Descripción |
|---|---|
| `axohologram.admin` | Acceso administrativo total |
| `axohologram.create` | Crear hologramas |
| `axohologram.delete` | Eliminar hologramas |
| `axohologram.edit` | Permiso legacy de edición general |
| `axohologram.reload` | Recargar el plugin |
| `axohologram.list` | Listar hologramas |
| `axohologram.teleport` | Teleport a hologramas |
| `axohologram.hologram.move` | Mover, rotar y cambiar offset base |
| `axohologram.hologram.visibility` | Editar visibilidad y view distance |
| `axohologram.hologram.style` | Editar estilo y apariencia |
| `axohologram.page.edit` | Editar páginas |
| `axohologram.line.edit` | Editar líneas |
| `axohologram.permission.edit` | Editar permisos de holograma |
| `axohologram.npc.edit` | Vincular y desvincular FancyNPCs |
| `axohologram.npc.info` | Ver información del vínculo con FancyNPCs |
| `axohologram.view.<id>` | Ver un holograma concreto si usa visibilidad por permiso |

## Formato de texto

Soporta:

- MiniMessage
- Legacy `&`
- Legacy `§`
- HEX `&#RRGGBB`
- HEX estilo Bungee `&x&F&F&0&0&F&F`

Ejemplos válidos:

```text
<red>Hola
&aHola
&#9D00FF&lTexto
&x&9&D&0&0&F&FTexto
```

## Integraciones opcionales

### PlaceholderAPI

Si está instalado y habilitado en config:

- se parsean placeholders por jugador en texto

### FancyNPCs

Si está instalado y habilitado en config:

- se pueden vincular hologramas a NPCs
- el holograma sigue la posición del NPC

### MiniPlaceholders

Si está instalado y habilitado en config:

- se parsean placeholders basados en MiniMessage
- se soportan placeholders globales y por audiencia
- los hologramas con placeholders detectados entran en el ciclo de refresh periódico

## API pública

<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.AxoStudio</groupId>
        <artifactId>AxoHologram</artifactId>
        <version>1.1.4-alpha</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

## Archivos de configuración

Se generan:

```text
plugins/AxoHologram/config.yml
plugins/AxoHologram/messages.yml
plugins/AxoHologram/holograms/
```

Cada holograma se guarda en su propio archivo:

```text
plugins/AxoHologram/holograms/spawn.yml
plugins/AxoHologram/holograms/shop.yml
plugins/AxoHologram/holograms/rules.yml
```

## Notas

- El plugin está orientado a **Paper**, no a Bukkit/Spigot como target principal.
- Los hologramas simples de texto pueden renderizarse como un solo `TextDisplay`.
- Los hologramas vinculados a FancyNPCs priorizan la posición del NPC sobre movimiento manual.
