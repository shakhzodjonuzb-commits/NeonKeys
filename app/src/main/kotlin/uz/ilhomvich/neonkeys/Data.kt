package uz.ilhomvich.neonkeys

object EmojiData {
    val TABS = listOf("😀", "👋", "🐱", "🍔", "⚽", "🚗", "💡", "❤️")

    private fun l(s: String) = s.trim().split(" ").filter { it.isNotEmpty() }

    val LISTS: List<List<String>> = listOf(
        l("😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 🤥 😶 😐 😑 😬 🙄 😯 😦 😧 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 🤑 🤠 😈 👿 👹 👺 🤡 💩 👻 💀 👽 👾 🤖"),
        l("👋 🤚 ✋ 🖖 👌 🤏 ✌️ 🤞 🤟 🤘 🤙 👈 👉 👆 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💪 👀 👅 👄 🧠 👶 👦 👧 👨 👩 👴 👵 🧔 👮 👷 💂 🕵️ 👸 🤴 🎅 🧙 🧛 🧟 🥷"),
        l("🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐨 🐯 🦁 🐮 🐷 🐸 🐵 🙈 🙉 🙊 🐔 🐧 🐦 🐤 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🐛 🦋 🐌 🐞 🐜 🐢 🐍 🦎 🐙 🦑 🦀 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐘 🦒 🐪 🌵 🌲 🌴 🌸 🌹 🌻"),
        l("🍏 🍎 🍐 🍊 🍋 🍌 🍉 🍇 🍓 🍒 🍑 🍍 🥭 🥝 🍅 🥑 🍆 🥕 🌽 🌶️ 🥔 🍞 🧀 🍗 🍖 🍔 🍟 🍕 🌭 🌮 🌯 🍝 🍜 🍲 🍣 🍤 🍦 🍩 🍪 🎂 🍰 🍫 🍬 🍭 🍿 ☕ 🍵 🥤 🍺"),
        l("⚽ 🏀 🏈 ⚾ 🎾 🏐 🏉 🎱 🏓 🏸 🥊 🥋 🎯 ⛳ 🎣 🎮 🕹️ 🎲 🧩 ♟️ 🎰 🎳 🎸 🎹 🎧 🎤 🎬 🎨 🏆 🥇 🥈 🥉 🏅"),
        l("🚗 🚕 🚙 🚌 🏎️ 🚓 🚑 🚒 🚜 🏍️ 🚲 ✈️ 🚀 🛸 🚁 ⛵ 🚢 🏠 🏰 🗽 🌍 🌙 ⭐ 🌟 ☀️ ⛅ 🌈 ⚡ 🔥 💧 ❄️ 🌊"),
        l("📱 💻 ⌨️ 🖥️ 🖱️ 💾 📷 📹 🎥 📞 ⏰ 🔋 🔌 💡 🔦 🔑 🔒 🔓 💎 💰 💳 🔧 🔨 ⚙️ 🧲 💣 📌 📎 ✏️ 📚 📝"),
        l("❤️ 🧡 💛 💚 💙 💜 🖤 🤍 💔 ❣️ 💕 💞 💓 💗 💖 💘 💯 ✅ ❌ ❓ ❗ ‼️ ⚠️ 🔞 ✨ 💥 💫 💢 💤 🎉 🎊 ➕ ➖ ✖️ ➗ ♻️ ⭕ 🔴 🔵")
    )
}

object Words {
    private fun l(s: String) = s.trim().split(" ").filter { it.isNotEmpty() }

    private val EN = l("the be to of and a in that have it for not on with he as you do at this but his by from they we say her she or an will my one all would there their what so up out if about who get which go me when make can like time no just him know take people into year your good some could them see other than then now look only come its over think also back after use two how our work first well way even new want because any these give day most us is are was were been has had did said hello hi thanks thank please sorry welcome yes okay love friend today tomorrow tonight morning night message phone call tell where why really maybe always never everything something someone sometimes probably tonight together happy birthday congratulations beautiful awesome amazing great nice cool where which while without because before between through another important different together understand")
    private val UZ = l("salom assalomu alaykum rahmat xayr yaxshi yomon men sen biz siz ular nima qanday qachon qayerda nega kim bu shu bor yoʻq ha mayli kerak kelaman boraman ishlayapman uyda ish maktab oila doʻst sevaman iltimos kechirasiz bugun ertaga kecha hozir keyin oldin bilan uchun haqida lekin ammo yoki va ham juda ancha katta kichik yangi eski chiroyli tez sekin kun tun tong kech hafta oy yil soat daqiqa pul narx olma non suv choy ovqat uy koʻcha shahar Toshkent Oʻzbekiston telefon internet dastur ilova oʻyin video rasm xabar yozing ayting hammasi hamma yana biroz albatta balki mumkin tushundim yaxshimisiz qalaysiz omad tabriklayman baxt sogʻlik salomatlik ishonch rahmatli marhamat xush kelibsiz qoyil zoʻr ajoyib mazali tinchlik xursand kutaman kutyapman qildim qilaman bormayman kelmayman bilmayman bilaman tushunarli")
    private val RU = l("привет спасибо пожалуйста да нет хорошо плохо я ты он она мы вы они что как когда где почему кто это там тут сейчас сегодня завтра вчера потом добрый день вечер утро ночь работа дом семья друг любовь деньги время телефон игра видео сообщение можно нужно надо очень тоже также только если чтобы потому который всегда никогда давай конечно наверное понял хочу могу буду есть быть идти делать говорить знать думать смотреть писать читать здравствуйте извините пока позже сколько почему сделать спокойной красиво отлично класс")

    fun list(lang: Int): List<String> = when (lang) { 1 -> UZ; 2 -> RU; else -> EN }
}
