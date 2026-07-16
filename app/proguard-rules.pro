# Regras conservadoras para release do LePraMim.
# Billing e ML Kit já publicam regras próprias, mas mantemos nomes de modelos locais
# úteis para logs/testes e futuras integrações.
-keep class com.lepramim.app.SmartReadingEngine$Result { *; }
-keep class com.lepramim.app.SmartReadingEngine$Signals { *; }

# Evita warnings de APIs opcionais usadas por bibliotecas do Google em alguns aparelhos.
-dontwarn com.google.android.gms.**
-dontwarn com.google.mlkit.**
