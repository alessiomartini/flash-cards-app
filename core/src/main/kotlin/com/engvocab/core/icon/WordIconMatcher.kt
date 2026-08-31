package com.engvocab.core.icon

/**
 * Picks an emoji that visually anchors a card's meaning, as a mnemonic aid - shown next to the
 * word wherever cards are displayed. Purely offline/static (no LLM or paid API involved): tries
 * an idiom-specific match first (since idioms' literal words rarely reflect their meaning), then
 * falls back to matching individual keywords in the term/definition/example against a curated
 * dictionary, and finally to a generic icon for the word's part of speech. Returns null when
 * nothing matches, leaving the final fallback (e.g. by card type) to the caller.
 */
object WordIconMatcher {

    fun match(front: String, definition: String?, example: String?, partOfSpeech: String?): String? {
        IDIOM_ICONS[normalize(front)]?.let { return it }

        for (token in tokens(front)) KEYWORD_ICONS[token]?.let { return it }
        for (token in tokens(definition.orEmpty())) KEYWORD_ICONS[token]?.let { return it }
        for (token in tokens(example.orEmpty())) KEYWORD_ICONS[token]?.let { return it }

        return partOfSpeechIcon(partOfSpeech)
    }

    private fun partOfSpeechIcon(partOfSpeech: String?): String? {
        val pos = partOfSpeech?.trim()?.lowercase() ?: return null
        return when {
            pos.startsWith("verb") -> "🏃"
            pos.startsWith("noun") -> "📦"
            pos.startsWith("adjective") -> "🎨"
            pos.startsWith("adverb") -> "💫"
            else -> null
        }
    }

    private fun normalize(text: String): String =
        text.trim().lowercase().replace('’', '\'').trim('.', ',', '!', '?', ';', ':', '"', '(', ')')

    private val TOKEN_PATTERN = Regex("[^a-zà-öø-ÿ']+")

    private fun tokens(text: String): List<String> =
        text.lowercase().split(TOKEN_PATTERN).filter { it.length > 2 && it !in STOP_WORDS }

    private val STOP_WORDS = setOf(
        "the", "and", "for", "are", "was", "were", "been", "being", "with", "that", "this",
        "its", "you", "your", "she", "he's", "she's", "they", "we", "not", "than", "then",
        "into", "out", "up", "down", "about", "from", "some", "any", "one", "his", "her",
        "their", "our", "who", "what", "when", "where", "how", "which", "will", "would",
        "could", "should", "have", "has", "had", "does", "did", "doing", "such", "these",
        "those", "there", "here", "very", "just", "also", "over", "under", "each", "more",
        "most", "other", "only", "own", "same", "too",
    )

    /** Common idioms/expressions, matched by their full normalized front - meaning, not literal words. */
    private val IDIOM_ICONS = mapOf(
        "spill the beans" to "🫘",
        "under the weather" to "🤒",
        "break a leg" to "🦵",
        "the ball is in your court" to "🎾",
        "miss the boat" to "🚢",
        "see eye to eye" to "👀",
        "by the skin of your teeth" to "🦷",
        "it's the best thing since sliced bread" to "🍞",
        "once in a blue moon" to "🌙",
        "hit the sack" to "🛏️",
        "to hit the road" to "🛣️",
        "to hit the books" to "📚",
        "beat around the bush" to "🌳",
        "it costs an arm and a leg / it costs a fortune" to "💸",
        "when pigs fly" to "🐷",
        "a hot potato" to "🥔",
        "to call it a day" to "📅",
        "cut it out" to "✂️",
        "on the house" to "🏠",
        "burn the candle at both ends" to "🕯️",
        "not my cup of tea" to "🍵",
        "the apple of one's eye" to "🍎",
        "take it with a pinch of salt" to "🧂",
        "castle in the air" to "🏰",
        "you can say that again" to "🔁",
        "look on the bright side" to "🌤️",
        "sat on the fence" to "🚧",
        "keep up the good work" to "👍",
        "jump on the bandwagon" to "🚌",
        "food for thought" to "🍽️",
        "over the moon" to "🌝",
        "to be on the same page" to "📄",
        "to know/read [sb/sth] like a book" to "📖",
        "elephant in the room" to "🐘",
        "to be in the doghouse" to "🐕",
        "to sweat blood" to "💦",
        "a new lease of life" to "🌱",
        "chin music" to "🎶",
        "pull someone's leg" to "🦵",
        "wolf in sheep's clothing" to "🐺",
        "to pass with flying colours" to "🎉",
        "hive of activity" to "🐝",
        "a cash cow" to "🐄",
        "the mark of a true hero is humility" to "🦸",
        "spitting image" to "🪞",
        "wear the trousers" to "👖",
        "the last straw" to "🥤",
        "to put all your eggs in one basket" to "🧺",
        "the tip of the iceberg" to "🧊",
        "it takes two to tango" to "💃",
        "as fit as a fiddle" to "🎻",
        "read my lips" to "👄",
        "fit like a glove" to "🧤",
        "from cover to cover" to "📖",
        "a closed book" to "📕",
        "flip-flops" to "🩴",
        "sound like a broken record" to "💿",
        "to be a tower of strength" to "🗼",
        "full of beans" to "🫘",
        "to blow your own trumpet" to "🎺",
    )

    private val KEYWORD_ICONS = mapOf(
        // animals
        "dog" to "🐶", "cat" to "🐱", "poodle" to "🐩", "goose" to "🦢", "geese" to "🦢",
        "rhino" to "🦏", "narwhal" to "🐋", "wolf" to "🐺", "wolves" to "🐺", "fox" to "🦊",
        "bear" to "🐻", "horse" to "🐴", "filly" to "🐴", "ox" to "🐂", "cow" to "🐄",
        "sheep" to "🐑", "pig" to "🐷", "turkey" to "🦃", "swan" to "🦢", "crane" to "🦩",
        "seagull" to "🐦", "poultry" to "🐔", "herd" to "🐄", "flock" to "🐑", "cub" to "🐻",
        // food
        "pepper" to "🌶️", "cinnamon" to "🧂", "basil" to "🌿", "sesame" to "🌾",
        "eggplant" to "🍆", "cucumber" to "🥒", "strawberry" to "🍓", "coconut" to "🥥",
        "hazelnut" to "🌰", "almond" to "🌰", "cashew" to "🥜", "chickpea" to "🫛",
        "lentil" to "🫘", "bean" to "🫘", "beans" to "🫘", "noodles" to "🍜",
        "dumpling" to "🥟", "pastry" to "🥐", "bread" to "🍞", "cheese" to "🧀",
        "yolk" to "🥚", "egg" to "🥚", "vinegar" to "🍶", "broth" to "🍲",
        "caviar" to "🐟", "sauce" to "🥫", "pizza" to "🍕", "coffee" to "☕",
        "sugar" to "🍬", "dough" to "🥯", "spinach" to "🥬",
        "zucchini" to "🥒", "pumpkin" to "🎃", "garlic" to "🧄", "cumin" to "🌿",
        "yeast" to "🍞", "loaf" to "🍞",
        // travel
        "airport" to "✈️", "flight" to "✈️", "boarding" to "🛫", "hotel" to "🏨",
        "beach" to "🏖️", "train" to "🚆", "bus" to "🚌", "map" to "🗺️",
        "embassy" to "🏛️", "passport" to "🛂", "journey" to "🧳", "voyage" to "🚢",
        "cruise" to "🚢", "excursion" to "🥾", "safari" to "🦁", "itinerary" to "🗺️",
        "resort" to "🏝️", "sightseeing" to "📸", "shuttle" to "🚐",
        // emotions
        "anger" to "😠", "angry" to "😠", "love" to "❤️", "joy" to "😄",
        "grief" to "😢", "fear" to "😨", "scared" to "😨",
        "jealousy" to "😒", "disgust" to "🤢", "surprise" to "😲", "shame" to "😳",
        "pride" to "😌", "anxiety" to "😰", "hope" to "🌈", "gratitude" to "🙏",
        "hatred" to "💢", "loathing" to "🤮", "delight" to "🥰", "despair" to "😞",
        "laughter" to "😂", "giggling" to "😆", "yearning" to "🥺", "longing" to "🥺",
        "outraged" to "😡", "appalled" to "😱", "startled" to "😳", "dismal" to "😞",
        "bleak" to "🌫️", "gloomy" to "🌥️", "empathy" to "🤝", "resentful" to "😤",
        // body
        "heart" to "❤️", "brain" to "🧠",
        "beard" to "🧔", "belly" to "🫃", "toe" to "🦶", "knuckle" to "✊",
        "claw" to "🦅", "fang" to "🦷", "fur" to "🐾",
        // nature
        "rain" to "🌧️", "weather" to "🌤️", "moon" to "🌙", "star" to "⭐",
        "tree" to "🌳", "flower" to "🌸", "fire" to "🔥", "water" to "💧",
        "wind" to "💨", "snow" to "❄️", "storm" to "⛈️", "thunder" to "⛈️",
        "lightning" to "⚡", "glacier" to "🧊", "waterfall" to "💦", "dew" to "💧",
        "drizzle" to "🌦️", "wilderness" to "🏞️", "cliff" to "⛰️",
        // family
        "mother" to "👩", "father" to "👨", "aunt" to "👩", "family" to "👨‍👩‍👧",
        "sibling" to "👫", "siblings" to "👫", "guardian" to "🧑‍🍼", "adoption" to "🤱",
        // work
        "job" to "💼", "money" to "💰", "salary" to "💵", "office" to "🏢",
        "boss" to "👔", "interview" to "🎤", "manager" to "🧑‍💼", "employee" to "🧑‍💻",
        "career" to "📈", "lawyer" to "⚖️", "doctor" to "🩺", "actor" to "🎭",
        "actress" to "🎭", "musician" to "🎸", "sculptor" to "🗿", "electrician" to "🔌",
        "surgeon" to "🩺", "farmer" to "🚜", "pharmacist" to "💊", "attorney" to "⚖️",
        // time
        "clock" to "⏰", "calendar" to "📅", "morning" to "🌅", "midday" to "🌞",
        "noon" to "🌞", "fortnight" to "📅",
        // home
        "house" to "🏠", "kitchen" to "🍳", "door" to "🚪", "bed" to "🛏️",
        "castle" to "🏰", "cabin" to "🛖", "bungalow" to "🏡", "fireplace" to "🔥",
        "chimney" to "🏚️", "balcony" to "🏢", "porch" to "🚪",
        // hobby
        "music" to "🎵", "book" to "📖", "dance" to "💃", "art" to "🎨",
        "painting" to "🖼️", "concert" to "🎤", "ballet" to "🩰", "melody" to "🎶",
        "rhythm" to "🥁", "gallery" to "🖼️", "poem" to "📜",
        // money/shopping
        "discount" to "🏷️", "receipt" to "🧾", "bargain" to "💰", "purchase" to "🛍️",
        "market" to "🛒", "boutique" to "👗", "supermarket" to "🛒",
        // actions
        "sleep" to "😴", "speak" to "🗣️",
        "write" to "✍️", "think" to "🤔", "walk" to "🚶", "swim" to "🏊",
        "fight" to "🥊", "whisper" to "🤫", "shout" to "📢", "yawn" to "🥱",
        "hug" to "🤗", "kiss" to "💋", "cough" to "🤧", "sneeze" to "🤧",
    )
}
