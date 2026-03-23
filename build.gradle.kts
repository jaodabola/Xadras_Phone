// Ficheiro de configuração de nível raiz — plugins comuns a todos os módulos.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.androidx.navigation.safeargs) apply false
}