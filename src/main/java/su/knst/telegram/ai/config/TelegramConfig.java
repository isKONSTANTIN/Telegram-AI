package su.knst.telegram.ai.config;

public class TelegramConfig {
    public String apiToken = "123456:XXXX";
    public long superAdminId = 0;
    public String startMessage = """
            Welcome to Your AI-Powered Telegram Assistant! 🤖✨

            Say hello to a new way of interacting with the digital world! Our AI Assistant is here to streamline your tasks, enhance your creativity, and empower your productivity. Here’s what you can do with our powerful bot:

            Features:

            📄 File Management:
            - Seamlessly read text from .pdf, .html, and .docx files.
            - Export your insights to .txt and .docx formats.

            🧠 Dynamic Context Modes:
            - Single Mode: No need to reply to the bot every time. The bot retains context in its memory.
            - Multi Mode: Reply to specific messages for accurate context recognition.

            📐 Enhanced Mathematical Abilities:
            - Leverage advanced mathematical tools to refine your queries and calculations.

            🎭 Telegram Reactions:
            - Engage with messages using instant reactions for a more interactive experience.

            🌐 Web Content Integration:
            - Automatically analyze if loading webpage text content is necessary — no explicit commands needed.

            🎨 DALL·E 3 Support:
            - Unleash your creativity with cutting-edge image generation capabilities.

            🔄 Real-time Message Updates:
            - If your last message is edited, the bot’s response updates automatically for ongoing relevance.

            🔧 Custom Presets:
            - Personalize your AI interactions with comprehensive preset configurations. Adjust prompts, temperature, token limits, and more for tailored responses.

            Available Commands:

            ➕ /add preset - Craft new presets to modify AI behavior.
            🗑️ /delete - Remove a specific or most recent context by replying to a message.
            🆕 /new - Initialize a fresh context, perfect for single context mode.
            ⚙️ /settings - Dive into the settings menu to tweak your bot experience.

            With these robust features and intuitive commands, your AI Assistant stands ready to assist you in a myriad of tasks. Enjoy a seamless, efficient, and engaging experience with your Telegram AI companion! 🚀
            """;
}
