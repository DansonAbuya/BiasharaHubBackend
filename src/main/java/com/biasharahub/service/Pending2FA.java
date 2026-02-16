package com.biasharahub.service;

import java.util.UUID;

public record Pending2FA(UUID userId, String email) {
}
