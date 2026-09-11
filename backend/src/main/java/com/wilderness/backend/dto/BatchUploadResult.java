package com.wilderness.backend.dto;

import java.util.List;

/**
 * 批量上传结果。单个文件失败不影响同批其它文件，失败原因逐条放在 items 里返回,
 * 由前端按文件展示,因此这里不抛异常、统一用 success 标记区分。
 */
public record BatchUploadResult(int succeeded, int failed, List<Item> items) {

    /** 成功时 result 有值、error 为 null;失败时反之。 */
    public record Item(String fileName, boolean success, String error, UploadResult result) {

        public static Item ok(UploadResult result) {
            return new Item(result.fileName(), true, null, result);
        }

        public static Item failed(String fileName, String error) {
            return new Item(fileName, false, error, null);
        }
    }
}
