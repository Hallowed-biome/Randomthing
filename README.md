# Discord Bot Manager

Aplicativo Android simples para conectar um bot do Discord, listar servidores e canais acessíveis, visualizar mensagens e enviar mensagens.

## Estrutura inicial

- `app/`: aplicativo Android em Kotlin.
- `MainActivity.kt`: primeira tela para inserir o token e testar a conexão com o Discord.
- `AndroidManifest.xml`: permissões e configuração do aplicativo.
- `.github/workflows/android.yml`: build automático do APK de depuração pelo GitHub Actions.

## Segurança

Nunca compartilhe o token do bot. O token é uma credencial secreta e deve ser tratado como uma senha. O aplicativo não registra o token em logs nem o inclui no código-fonte. Nesta primeira versão, o token fica apenas em memória durante a execução.
