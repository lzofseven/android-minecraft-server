scoreboard players add #global timer 1
execute if score #global timer matches 5.. run function gemini:crumble
execute as @a[gamemode=adventure] at @s if block ~ ~-0.1 ~ red_glass run gamemode spectator @s
execute as @a[gamemode=adventure] at @s if block ~ ~-0.1 ~ red_glass run playsound entity.generic.explode master @a ~ ~ ~ 1 1
