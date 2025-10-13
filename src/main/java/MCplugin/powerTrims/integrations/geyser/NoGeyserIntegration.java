package MCplugin.powerTrims.integrations.geyser;

import java.util.UUID;

public class NoGeyserIntegration implements GeyserIntegration {
    @Override
    public boolean isBedrockPlayer(UUID playerUUID) {
        return false;
    }
}
