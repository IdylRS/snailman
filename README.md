# Snailman Mode

A wise man once asked, "What would you do if you had a snail following you around
and if it touched you, you'd die instantly?"

I have created a plugin that seeks to answer that very question.

## How does it work?

This plugin adds a "snail" to the Kebos Lowlands, and uses a modified version of the
[Shortest Path plugin](https://github.com/Skretzo/shortest-path) to find it's way to you. It moves
at a player's walking speed (1 tile per tick).

If the snail ever touches you, you will be alerted in your chatbox that you have died
and your green "SnailMan" icon will disappear.

If you want to reset the snail and your status, you can just hit the "Reset Snail Data" button in the
SnailMan Mode panel.

## Where can the snail go?

Lots of places, but not all of them. The shortest path plugin handles a lot of 
stairs, ladders, etc. and it's really great for that, but it's not exhaustive.
There are definitely places you can hide from the snail, but that's not really fun is it?

The snail also has the ability to use most agility shortcuts, the standard spirit trees,
charter ships, and access Priffdinas. It cannot, however, access instanced areas such as 
The Gauntlet, the POH, etc.