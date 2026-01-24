execute as @a at @s if block ~ ~-0.1 ~ red_concrete run fill ~ ~-0.1 ~ ~ ~-0.1 ~ air replace red_concrete
execute as @a at @s if block ~ ~-0.1 ~ red_concrete run particle smoke ~ ~ ~ 0.5 0.5 0.5 0.05 10
execute as @a at @s if block ~ ~-0.1 ~ yellow_concrete run fill ~ ~-0.1 ~ ~ ~-0.1 ~ red_concrete replace yellow_concrete
execute as @a at @s if block ~ ~-0.1 ~ green_concrete run fill ~ ~-0.1 ~ ~ ~-0.1 ~ yellow_concrete replace green_concrete
execute as @a at @s if block ~ ~-0.1 ~ white_concrete run fill ~ ~-0.1 ~ ~ ~-0.1 ~ green_concrete replace white_concrete
execute as @a at @s if block ~ ~-0.1 ~ white_concrete run playsound block.note_block.bit master @a ~ ~ ~ 1 1
scoreboard players set #global timer 0
