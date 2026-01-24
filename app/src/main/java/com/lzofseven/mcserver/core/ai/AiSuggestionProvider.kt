package com.lzofseven.mcserver.core.ai

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class AiSuggestion(
    val label: String,
    val detailedPrompt: String,
    val icon: ImageVector
)

object AiSuggestionProvider {

    private val allSuggestions = listOf(
        // === CONSTRUÇÃO E ARQUITETURA ===
        AiSuggestion("Castelo Medieval", "Construa um castelo medieval completo no Minecraft com ponte levadiça, masmorra, torres circulares e janelas de vitral, usando blocos de pedra e madeira de carvalho escuro.", Icons.Default.Castle),
        AiSuggestion("Torre de Vigia", "Projete uma torre de vigia alta e detalhada em um pico nevado, usando pedregulho musgoso, troncos de abeto e lanternas para iluminação atmosférica.", Icons.Default.Explore),
        AiSuggestion("Muralha Defensiva", "Crie uma muralha defensiva impenetrável ao redor da vila, com ameias, passarelas para arqueiros e portões automáticos usando redstone.", Icons.Default.Security),
        AiSuggestion("Forte do Nether", "Construa uma fortaleza compacta no Nether usando tijolos do nether vermelhos e basalto polido, incluindo defesas contra Ghasts.", Icons.Default.Terrain),
        AiSuggestion("Palácio de Gelo", "Desenvolva um palácio de gelo majestoso usando gelo compactado e azul, com grandes salões espelhados e colunas de quartzo.", Icons.Default.AcUnit),
        AiSuggestion("Farol Marítimo", "Construa um farol funcional na costa com um sistema de luz rotatória, usando concreto branco e vermelho, e uma base de pedregulho.", Icons.Default.Lightbulb),
        AiSuggestion("Vila Viking", "Gere uma pequena vila de estilo viking com casas de telhado de palha, barcos longos no porto e uma grande fogueira central para banquetes.", Icons.Default.Home),
        AiSuggestion("Mansão Moderna", "Projete uma mansão moderna minimalista com grandes janelas de vidro fumê, uma piscina infinita e interiores de concreto branco com acabamento em madeira.", Icons.Default.Home),
        AiSuggestion("Pirâmide Egípcia", "Construa uma pirâmide gigante de arenito com uma câmara mortuária secreta, armadilhas de flechas e tesouros escondidos no centro.", Icons.Default.History),
        AiSuggestion("Templo Japonês", "Crie um templo japonês tradicional (pagode) de vários andares com telhados curvos, portões Torii e um jardim de cerejeiras ao redor.", Icons.Default.Castle),
        AiSuggestion("Ponte Suspendida", "Construa uma ponte suspensa detalhada sobre o desfiladeiro, usando correntes, cercas de abeto e blocos de lama para um visual rústico.", Icons.Default.Architecture),
        AiSuggestion("Catedral Gótica", "Desenvolva uma imensa catedral gótica com arbotantes, gárgulas de pedra e um interior com abóbadas de cruzaria de pedra.", Icons.Default.AccountBalance),
        AiSuggestion("Base Subaquática", "Construa uma base submarina futurista em forma de cúpula feita de vidro e prismarinho, com entradas pressurizadas e laboratórios de poções.", Icons.Default.Waves),
        AiSuggestion("Observatório Astro", "Crie um observatório astronômico em uma montanha alta, com uma cúpula de cobre oxidado e um telescópio gigante feito de vidro e blocos de ferro.", Icons.Default.Public),
        AiSuggestion("Moinho de Vento", "Projete um moinho de vento rústico com pás de lã branca, uma casa de fazenda anexa e campos de flores ao redor.", Icons.Default.WbSunny),
        AiSuggestion("Estação de Trem", "Construa uma estação de trem vitoriana detalhada com plataformas cobertas, relógios de parede e um sistema de trilhos organizado.", Icons.Default.Train),
        AiSuggestion("Ruínas Antigas", "Gere um conjunto de ruínas de uma civilização perdida, com estátuas quebradas, vegetação densa e blocos de ouro escondidos.", Icons.Default.BrokenImage),
        AiSuggestion("Skyscraper", "Construa um arranha-céu moderno de 50 andares usando concreto cinza e painéis de vidro azul, com um heliponto no topo.", Icons.Default.Domain),
        AiSuggestion("Bunker Secreto", "Desenvolva um bunker subterrâneo de segurança máxima com portas de ferro, estoques de suprimentos e uma sala de controle de redstone.", Icons.Default.Lock),
        AiSuggestion("Mercado de Rua", "Crie um mercado medieval vibrante com barracas coloridas, carrinhos de comida e fardos de feno espalhados.", Icons.Default.Storefront),

        // === AUTOMAÇÃO E FAZENDAS ===
        AiSuggestion("Fazenda de Trigo", "Crie uma fazenda de trigo 100% automática usando aldeões (villagers) para colher e um sistema de coleta com funis e baús.", Icons.Default.Agriculture),
        AiSuggestion("Gerador de Cobble", "Desenvolva um gerador de pedregulho de alta velocidade que empurra os blocos com pistões para uma área de mineração central e segura.", Icons.Default.DynamicForm),
        AiSuggestion("Fazenda de Bambu", "Instale uma fazenda de bambu infinita usando observadores e pistões para colheita instantânea e transporte via água.", Icons.Default.AlignVerticalBottom),
        AiSuggestion("Cana de Açúcar", "Crie um módulo de cana-de-açúcar expansível que usa trilhos com funil para coletar tudo automaticamente sem perdas.", Icons.Default.VerticalAlignBottom),
        AiSuggestion("Criadouro de Ovelhas", "Projete um sistema de tosquia automática de ovelhas por cor, usando tesouras em ejetores e separadores de itens.", Icons.Default.Palette),
        AiSuggestion("Fazenda de Ferro", "Construa uma fazenda de ferro eficiente suspensa com zumbis e aldeões, coletando lingotes no centro e matando golens em lava.", Icons.Default.Factory),
        AiSuggestion("Coleta de Abóboras", "Crie uma fazenda compacta de abóboras e melancias usando observadores de bloco e pistões colados para eficiência máxima.", Icons.Default.Fastfood),
        AiSuggestion("Processador de Algas", "Desenvolva um sistema de algas secas automático que colhe, transporta e cozinha os itens em fornalhas industriais.", Icons.Default.SoupKitchen),
        AiSuggestion("Forno Industrial", "Construa um super-smelter que distribui itens igualmente entre 16 fornalhas usando vagonetes com funis (minecarts).", Icons.Default.Whatshot),
        AiSuggestion("Fazenda de Cactos", "Crie uma torre de fazenda de cactos passiva que usa cercas para quebrar os blocos automaticamente conforme crescem.", Icons.Default.AddLocation),
        AiSuggestion("Elevador de Itens", "Projete um elevador de itens silencioso usando colunas de bolhas de Soul Sand para levar recursos da mina até a superfície.", Icons.Default.ArrowUpward),
        AiSuggestion("Separador Itens", "Instale um sistema de filtragem de itens inteligente que organiza automaticamente diamantes, blocos e lixo em baús diferentes.", Icons.Default.FilterList),
        AiSuggestion("Fazenda de Raid", "Construa um sistema avançado acima do oceano para farmar ataques de Illagers e obter Totens da Imortalidade e esmeraldas infinitas.", Icons.Default.Gavel),
        AiSuggestion("Fábrica de Mel", "Crie uma fazenda de mel usando colmeias, ejetores com frascos e um sistema de flores fechado com vidro para as abelhas.", Icons.Default.Hive),
        AiSuggestion("Fazenda de Ouro", "Desenvolva uma plataforma de farm de Piglins no teto do Nether usando atração de ovos de tartaruga e matadouros em queda.", Icons.Default.AttachMoney),
        AiSuggestion("Criadouro de Peixes", "Crie um tanque de pesca semi-automático com proteção de AFK e sistema de baús para coletar tesouros de pesca.", Icons.Default.Phishing),
        AiSuggestion("Lavanderia de Lã", "Implemente um sistema que separa lã por 16 cores diferentes em uma fileira de baús organizada e iluminada.", Icons.Default.CleaningServices),
        AiSuggestion("Fábrica de Pedregulho", "Desenvolva uma máquina voadora (flying machine) que gera e quebra pedregulho em massa usando TNT duping controlado.", Icons.Default.AirplanemodeActive),
        AiSuggestion("Estoque de Vidro", "Crie um deserto artificial interno que usa TNT para cavar areia e fornalhas automáticas para produzir vidro em escala.", Icons.Default.BlurOn),
        AiSuggestion("Coletor de Madeira", "Projete uma fazenda de árvores automática que usa o crescimento acelerado com farinha de osso e empurra os troncos com pistões.", Icons.Default.Park),

        // === MINIGAMES E SISTEMAS ===
        AiSuggestion("Arena Spleef", "Gere uma arena de Spleef clássica com chão de neve e um sistema que detecta quando o jogador cai para eliminá-lo.", Icons.Default.SportsEsports),
        AiSuggestion("Corrida de Barco", "Construa uma pista de corrida de gelo azul com curvas fechadas, saltos e um contador de voltas usando scoreboard.", Icons.Default.DirectionsBoat),
        AiSuggestion("Parkour Challenge", "Crie um percurso de parkour gerado proceduralmente com níveis de dificuldade crescente e checkpoints de spawnpoint.", Icons.Default.DirectionsRun),
        AiSuggestion("Labirinto de Vidro", "Gere um labirinto complexo feito de vidro tingido que muda de cor dinamicamente usando o comando fill e loops de tick.", Icons.Default.Extension),
        AiSuggestion("Batalha Tnt", "Crie um minigame de duelo de canhões de TNT balanceado, com zonas de proteção e recarga automática de munição.", Icons.Default.Dangerous),
        AiSuggestion("PVP Gladiator", "Construa uma arena circular de PvP com portões automáticos, sistema de classes (Guerreiro, Arqueiro) e bônus de cura no centro.", Icons.Default.Handyman),
        AiSuggestion("Tiro ao Alvo", "Crie um jogo de tiro ao alvo usando botões de madeira e arco, com luzes de redstone que indicam se você acertou o centro ou a borda.", Icons.Default.AdsClick),
        AiSuggestion("Sumo de Slime", "Desenvolva um minigame de Sumo em uma pequena plataforma flutuante, onde os jogadores usam gravetos com Repulsão II.", Icons.Default.ControlCamera),
        AiSuggestion("Corrida de Obstáculos", "Crie uma pista de obstáculos com armadilhas de flechas, pistões que te empurram e poções de lentidão no caminho.", Icons.Default.List),
        AiSuggestion("Defesa da Base", "Projete um minigame 'Wave Defense' onde hordas de zumbis atacam a base e o jogador ganha pontos para comprar itens novos.", Icons.Default.Security),
        AiSuggestion("Roleta Russa TNT", "Crie um sistema aleatório de redstone onde os jogadores pisam em placas de pressão; uma delas ativa uma TNT e elimina o jogador.", Icons.Default.Casino),
        AiSuggestion("Capture a Bandeira", "Implemente um sistema de CTF com duas bases, bandeiras representadas por banners e detecção de quando a bandeira inimiga entra na base.", Icons.Default.Flag),
        AiSuggestion("Quiz de Minecraft", "Desenvolva um sistema de perguntas e respostas usando tellraw; acertos dão esmeraldas e erros dão um pequeno choque elétrico.", Icons.Default.QuestionMark),
        AiSuggestion("Pesca Premiada", "Crie um campeonato de pesca onde quem pescar o item mais raro em 2 minutos vence e recebe uma mensagem global de vitória.", Icons.Default.Phishing),
        AiSuggestion("Duelo de Arco", "Construa duas plataformas distantes sobre a lava para um duelo de arco com flechas incendiárias e plataformas que somem.", Icons.Default.JoinRight),
        AiSuggestion("Escalada Extrema", "Crie uma parede de escalada vertical de 100 blocos com obstáculos móveis e vento lateral (efeito de levitação).", Icons.Default.Landscape),
        AiSuggestion("Esconde-Esconde", "Configure um minigame de esconde-esconde com tags para o buscador, temporizador de 5 minutos e modo aventura para os escondidos.", Icons.Default.PersonSearch),
        AiSuggestion("Bombardeio Aéreo", "Crie um jogo onde jogadores em Elytras precisam soltar baús explosivos em alvos terrestres enquanto evitam fogo antiaéreo.", Icons.Default.Flight),
        AiSuggestion("Minigame da Memória", "Desenvolva um sistema que mostra uma sequência de blocos coloridos e o jogador precisa repetir a ordem exata pisando em botões.", Icons.Default.Memory),
        AiSuggestion("Trilha de Fogo", "Crie uma corrida em que o chão atrás de você desaparece (fill air) conforme você corre, forçando você a nunca parar.", Icons.Default.LocalFireDepartment),

        // === GERENCIAMENTO E QOL ===
        AiSuggestion("Limpar Drop", "Crie um comando para limpar todos os itens jogados no chão (entities) a cada 5 minutos para reduzir o lag do servidor.", Icons.Default.DeleteSweep),
        AiSuggestion("Menu de Teleporte", "Desenvolva um sistema de teleporte via chat (tellraw) para pontos turísticos do servidor: Spawn, Vila e Arena.", Icons.Default.LocationOn),
        AiSuggestion("Ciclo de Tempo", "Configure um ciclo de tempo personalizado que dura 40 minutos (em vez de 20), rotacionando clima e iluminação.", Icons.Default.Timer),
        AiSuggestion("Proteção de Spawn", "Implemente uma zona de proteção no spawn onde jogadores não podem quebrar blocos ou sofrer dano (modo aventura automático).", Icons.Default.VerifiedUser),
        AiSuggestion("Kit Inicial", "Crie um comando que entrega um kit de ferramentas de pedra e comida apenas para novos jogadores que entram pela primeira vez.", Icons.Default.CardGiftcard),
        AiSuggestion("Sistema de Economia", "Implemente um sistema básico de economia usando moedas virtuais (scoreboards) e uma loja onde é possível comprar itens raros.", Icons.Default.Money),
        AiSuggestion("Chat Global", "Crie um sistema que reformata o chat do servidor com prefixos de cargos (Dono, VIP, Membro) e cores diferenciadas automaticamente.", Icons.Default.Chat),
        AiSuggestion("Detector de X-Ray", "Desenvolva um log que notifica o console quando um jogador encontra e quebra mais de 20 minérios de diamante em 1 minuto.", Icons.Default.Search),
        AiSuggestion("Aviso de Reinício", "Crie um script que avisa no chat 'Servidor reiniciando em 1 minuto!' com contagem regressiva e som de alarme.", Icons.Default.PriorityHigh),
        AiSuggestion("Voz de Próx.", "Simule um chat de voz por proximidade mandando mensagens de chat apenas para jogadores dentro de um raio de 10 blocos.", Icons.Default.Hearing),
        AiSuggestion("Rank de Votos", "Implemente um ranking global (tab ou chat) dos jogadores com mais tempo de jogo ou mais blocos minerados no servidor.", Icons.Default.Leaderboard),
        AiSuggestion("Regras do Servidor", "Crie um menu de regras interativo que aparece na tela quando o jogador digita /rules ou entra no servidor pela primeira vez.", Icons.Default.Rule),
        AiSuggestion("Auto-Moderação", "Configure um sistema que bloqueia palavras ofensivas no chat e manda um aviso automático para quem infringir as regras.", Icons.Default.Block),
        AiSuggestion("Dashboard Server", "Crie uma mensagem tellraw periódica que mostra o status do servidor: jogadores online, TPS atual e tempo de uptime.", Icons.Default.Dashboard),
        AiSuggestion("Cargos Automáticos", "Desenvolva um script que atribui o cargo 'Veterano' para jogadores que atingirem 24 horas de jogo real no servidor.", Icons.Default.Star),
        AiSuggestion("Backup Automático", "Configure uma tarefa agendada que salva o mundo e desabilita a escrita em disco por 10 segundos para fins de backup externo.", Icons.Default.Backup),
        AiSuggestion("Sistema de Warps", "Implemente um comando compacto /warp [nome] capaz de lidar com 20 destinos diferentes salvos em scoreboards.", Icons.Default.FastForward),
        AiSuggestion("Anti-Griefing", "Crie um comando que reverte todas as explosões de Creeper e TNT instantaneamente usando o histórico de blocos alterados.", Icons.Default.Replay),
        AiSuggestion("Teleporte de Equipe", "Desenvolva uma ferramenta administrativa para teleportar todos os jogadores de uma só vez para a posição do administrador.", Icons.Default.Groups),
        AiSuggestion("Reloop de Tick", "Otimize todos os comandos de execução contínua para rodarem apenas uma vez por segundo (20 ticks) para poupar CPU.", Icons.Default.DataUsage),

        // === ESTÉTICA E DETALHES ===
        AiSuggestion("Sistema de Partículas", "Crie um rastro de partículas de notas musicais e corações que segue o jogador VIP conforme ele se move pelo mundo.", Icons.Default.AutoAwesome),
        AiSuggestion("Iluminação de Rua", "Instale postes de luz automáticos na vila que usam sensores de luz solar para ligar lâmpadas de redstone à noite.", Icons.Default.NightsStay),
        AiSuggestion("Jardim Zen", "Projete um jardim zen com areia, botões fazendo as pedras e cercas com folhas de azaleia fazendo os bonsais detalhados.", Icons.Default.Grass),
        AiSuggestion("Aquário de Parede", "Construa um aquário gigante embutido na parede com corais coloridos, algas e peixes tropicais spawnados nela.", Icons.Default.Tsunami),
        AiSuggestion("Interior Moderno", "Crie móveis modernos usando escadas de quartzo para sofás, banners para cortinas e molduras para prateleiras de livros.", Icons.Default.Chair),
        AiSuggestion("Esculturas de Neve", "Gere estátuas gigantes de mobs do Minecraft (Creepers, Endermans) feitas inteiramente de blocos de neve e quartzo.", Icons.Default.Palette),
        AiSuggestion("Show de Fogos", "Desenvolva um show de fogos de artifício sincronizado com música, ativado por uma única alavanca central de controle.", Icons.Default.Celebration),
        AiSuggestion("Mercado Galático", "Construa uma área de trocas futurista usando blocos de obsidiana chorosa, end stone e decorações de luz roxa.", Icons.Default.RocketLaunch),
        AiSuggestion("Bosque de Outono", "Crie uma floresta artificial usando blocos de cobre oxidado, lã laranja e amarela para simular as cores do outono.", Icons.Default.Eco),
        AiSuggestion("Estúdio de Poções", "Desenvolva um laboratório de alquimia detalhado com caldeirões, estantes de poções e efeitos de fumaça subindo pela chaminé.", Icons.Default.Science),
        AiSuggestion("Sala de Troféus", "Construa uma sala de troféus épica com pedestais para o Ovo do Dragão, a Estrela do Nether e cabeças de mobs raros.", Icons.Default.EmojiEvents),
        AiSuggestion("Decoração de Natal", "Instale árvores de natal com luzes de redstone piscantes e presentes gigantes (baús decorados) em toda a praça central.", Icons.Default.CardGiftcard),
        AiSuggestion("Caminhos de Pedra", "Gere caminhos naturais e quebradiços usando uma mistura de cascalho, pedregulho e andesito para um visual medieval realista.", Icons.Default.Timeline),
        AiSuggestion("Porto de Barcos", "Construa um porto detalhado com guindastes de carga, armazéns de madeira e ancoradouros para barcos de todos os tamanhos.", Icons.Default.Anchor),
        AiSuggestion("Interior Steampunk", "Crie uma sala de máquinas estilo steampunk com engrenagens de redstone, canos de cobre e fumaça saindo de pistões.", Icons.Default.Settings),
        AiSuggestion("Cemitério Assombrado", "Desenvolva um cemitério gótico com lápides inclinadas, teias de aranha, neblina rasteira e sons de ambiente assustadores.", Icons.Default.History),
        AiSuggestion("Base na Árvore", "Construa uma casa na árvore gigante (Jungle Tree) com várias plataformas conectadas por pontes de corda e elevadores de água.", Icons.Default.Nature),
        AiSuggestion("Sala de Cinema", "Projete um cinema interno com cadeiras de lã vermelha, uma tela imensa feita de concreto preto e luzes que se apagam.", Icons.Default.Movie),
        AiSuggestion("Taverna Rustica", "Crie uma taberna aconchegante com balcão de madeira, mesas de barril e prateleiras cheias de poções decorativas.", Icons.Default.LocalBar),
        AiSuggestion("Estátua do Jogador", "Gere uma estátua gigante de 20 blocos de altura do 'Steve' clássico no centro do spawn, usando blocos coloridos de lã.", Icons.Default.Accessibility),
        
        // === TERRAFORMING E AMBIENTE ===
        AiSuggestion("Rio Artificial", "Crie um rio sinuoso com margens de areia e vegetação, conectando dois lagos e incluindo pequenas pontes de madeira.", Icons.Default.Waves),
        AiSuggestion("Caverna de Cristais", "Desenvolva uma caverna geoda personalizada com cristais de ametista gigantes saindo das paredes e iluminação roxa.", Icons.Default.Diamond),
        AiSuggestion("Vulcão Ativo", "Construa um vulcão imponente com magma escorrendo, fumaça preta no topo e pedras queimadas ao redor da base.", Icons.Default.Fireplace),
        AiSuggestion("Oásis no Deserto", "Gere um oásis paradisíaco no meio do bioma de deserto, com palmeiras, água cristalina e uma pequena tenda de descanso.", Icons.Default.Spa),
        AiSuggestion("Montanha Nevada", "Crie uma montanha artificial de 50 blocos with picos pontiagudos de neve e caminhos de gelo para exploração vertical.", Icons.Default.Terrain)
    )
    
    // Adicionando dinamicamente mais para exemplificar o volume solicitado
    init {
        // Preencheríamos o restante aqui para atingir 100 com variações de materiais, temas e complexidade
    }

    fun getRandomSuggestions(limit: Int = 6): List<AiSuggestion> {
        return allSuggestions.shuffled().take(limit)
    }
}
