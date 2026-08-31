package com.wilderness.backend.dto;

/**
 * 收藏的天体快照，字段形状与前端 useFavorites 一致。
 */
public record FavoriteDTO(String slug, String zhName, String enName, String image) {
}
