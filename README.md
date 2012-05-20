Writable - write on paper, no chat commands required!

A new plugin for writing and reading text on paper, with a twist of realism.

Designed to not require any chat commands or client modifications.

**[Download Writable 1.2](http://dev.bukkit.org/server-mods/writable/files/7-writable-1-2/)** - released on 2012/05/20 for 1.2.5-R2.0

## Usage

To write, you will need paper and:

1. a writing implement (example: feather)
2. a writing surface (examples: stone or wood, not gravel or grass)
3. ink (example: dandelion yellow)

Ensure the writing implement is in your inventory hotbar, directly
next to the ink you want to write with, then while holding the paper
simply **double right-click** on a temporary writing surface present
somewhere in the environment.

A text entry dialog will appear, and you can type your story. 

To read a piece of paper, either left-click while holding the paper,
or simply hold it in your hand and the text will be displayed in the chat
(autoRead configuration option).

## Support

### Writing Implements
Feathers are the most logical choice of writing instrument, but several items are supported:

* Feather
* Stick
* Blaze rod
* Arrow

All function equivalently.

### Writing Surfaces
Almost any reasonable hard surface is acceptable as a writing surface.
Think of if you could place paper on it and write on the surface
in real life. Trying to hold your paper on, say, gravel or grass, and write on it
would not be very realistic. Stone, wood, and most other solid surfaces
are allowed by default.

Currently, not all logical writing surfaces are allowed -- crafting tables,
for example, will invoke the crafting grid instead.

### Inks
* Rose Red (dark red)
* Dandelion Yellow (yellow)
* Lapis Lazuli (blue)
* Cactus Green (dark green)
* Ink Sac (black)
* Bone Meal (white)
* Cyan Dye (blue)
* Purple Dye (light purple)
* Gray Dye (dark gray)
* Light Blue Dye (aqua)
* Pink Dye (red)
* Lime Dye (green)
* Magenta Dye (dark purple)
* Light Gray Dye (gray)
* Coal (dark gray)
* Charcoal (dark gray)
* Glowstone Dust (gold)
* Redstone Dust (magic)

Inks are optionally consumed when used (consumeInk configuration option).

Magic ink displays randomly alternating characters (same as used in The End credits).
It can be decoded to randomly colored text by reading the paper while a dragon egg is in your inventory
(configurable to any material; see the magicInkDecoder option).

## Technical Details

Writable listens for several consecutive events in order to cause
"double right-click paper" to show a text entry dialog. First, right-clicking
the paper triggers a player interaction event, and Writable quickly replaces
the paper with a temporary sign item. The second right-click places the sign,
triggering a block place event, and then a sign change event after the player
completes the text entry. 

Once the temporary sign is placed, the text is captured, written to the paper,
the sign is removed from the environment, and the paper is restored in the
player's inventory slot. If all goes well, these processes behind the scenes
should not be too noticeable nor immersion-breaking to the player. This technique
was conceived so that writing is possible without resorting to /commands.



## Limitations

By design, the text is limited to approximately one chat window screenful
per piece of paper. After all, it is paper, not a book.

The writing is in permanent ink; it isn't erasable. To start over, you have
to use a new paper.

For a more sophisticated text writing/reading plugin, see also [BookWorm](http://dev.bukkit.org/server-mods/bookworm/).

***[Fork me on GitHub](https://github.com/mushroomhostage/Writable)***
