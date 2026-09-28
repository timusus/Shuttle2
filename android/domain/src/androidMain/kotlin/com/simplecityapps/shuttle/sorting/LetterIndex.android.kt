package com.simplecityapps.shuttle.sorting

import java.text.Normalizer

internal actual fun baseLetter(letter: Char): Char = Normalizer.normalize(letter.toString(), Normalizer.Form.NFD).first()
