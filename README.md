# Where Is My Shulker

This mod keeps track of every shulker box you place or break in a world so you never again have to wander around your world searching desparately for your valuables.

It adds a simple command to interact with the tracked data.

[Download on Modrinth](https://modrinth.com/mod/where-is-my-shulker)

---

## ✨ Features

* Tracks all placed and broken shulker boxes
* Records:
    * **Location**
    * **Custom name** (if set)
    * **Shulker box color**
* Lists all currently placed shulkers on demand
* Shulker box list supports pagination for messy evenings
* Simple command to clean the list
* Stores data in csv format

---

## 📜 Commands

### `/shulker`

Displays a list of all currently placed shulker boxes, including:

* Custom name or box color
* Coordinates
* Color

The output is paginated, if necessary.

### `/shulker clear`

Clears all unnamed and undyed shulker boxes from the list.

### `/shulker clearall`

Clears *all* shulker boxes from the list.

---

## 💾 Data Storage

All data is saved as a CSV file. For *Singleplayer* worlds the path is `<world folder>/data/shulker_boxes.csv`.

On *Multiplayer* servers shulker boxes are stored in `.minecraft/.whereismyshulker/<serverip>_<port>/shulker_boxes.csv`

---

## 🧱 Notes

* Only shulkers broken by the player get removed. Explosions or piston movement isn't tracked.
* I've searched extensively for an existing solution to this issue but without any success. I did find 9 year old reddit posts asking the same question, but since this appears to be an unsolved problem I wanted to take it into my own hands, since I regularly struggle with lost shulker boxes. 
