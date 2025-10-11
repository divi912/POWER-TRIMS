package MCplugin.powerTrims.integrations.geyser;

import org.geysermc.geyser.api.GeyserApi;

import java.util.UUID;

public class GeyserIntegrationImpl implements GeyserIntegration {
    @Override
    public boolean isBedrockPlayer(UUID playerUUID) {
        return GeyserApi.api().isBedrockPlayer(playerUUID);
    }
}
