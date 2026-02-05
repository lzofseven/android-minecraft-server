# Limpa e constrói plataforma de neve
execute at @p run fill ~-7 ~-1 ~-7 ~7 ~-1 ~7 snow_block
execute at @p run fill ~-8 ~-1 ~-8 ~8 ~2 ~8 glass outline
execute at @p run tellraw @a {"text":"[Antigravity] Arena de Spleef construída ao seu redor!","color":"aqua"}
