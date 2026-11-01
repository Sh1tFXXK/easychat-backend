package handler

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
)

// 认证上下文里存 userID 的 key。
// 用带前缀的常量而不是裸字符串 "userID"：避免和第三方中间件的 key 撞车。
const ctxUserIDKey = "auth.userID"

// 上传目录与限制。
const (
	uploadDir     = "uploads"
	maxUploadSize = 10 << 20 // 10MB
)

var (
	errUploadTooLarge = errors.New("file too large")
	errUploadBadType  = errors.New("unsupported file type")
)

var imageExts = map[string]bool{
	".jpg": true, ".jpeg": true, ".png": true, ".gif": true, ".webp": true,
}

// currentUserID 从请求上下文取当前用户。
//
// 取值优先级：
//  1. 认证中间件解析 Bearer token 后写入的 c.Set(ctxUserIDKey, userID)；
//  2. 开发期兜底：X-User-ID 请求头（**可伪造，禁止上生产**）。
//
// 生产化路线：删掉第 2 步，让所有业务接口强制走 token。
func currentUserID(c *gin.Context) (uint64, bool) {
	if v, ok := c.Get(ctxUserIDKey); ok {
		if id, ok := v.(uint64); ok && id != 0 {
			return id, true
		}
	}

	raw := c.GetHeader("X-User-ID")
	if raw == "" {
		return 0, false
	}
	id, err := strconv.ParseUint(raw, 10, 64)
	if err != nil || id == 0 {
		return 0, false
	}
	return id, true
}

// requireUser 是 currentUserID 的"取不到就回 401"版本，省掉每个 handler 的三行样板。
func requireUser(c *gin.Context) (uint64, bool) {
	id, ok := currentUserID(c)
	if !ok {
		c.JSON(http.StatusUnauthorized, errorBody("missing or invalid credentials", http.StatusUnauthorized))
		return 0, false
	}
	return id, true
}

// parseUintParam 解析路径参数。
func parseUintParam(c *gin.Context, name string) (uint64, bool) {
	id, err := strconv.ParseUint(c.Param(name), 10, 64)
	if err != nil || id == 0 {
		c.JSON(http.StatusBadRequest, errorBody("invalid "+name, http.StatusBadRequest))
		return 0, false
	}
	return id, true
}

// parseUintQuery 解析 query 参数（缺省返回 0, true）。
func parseUintQuery(c *gin.Context, name string) (uint64, bool) {
	raw := c.Query(name)
	if raw == "" {
		return 0, true
	}
	id, err := strconv.ParseUint(raw, 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, errorBody("invalid "+name, http.StatusBadRequest))
		return 0, false
	}
	return id, true
}

// saveUpload 保存 multipart 文件并返回可访问 URL。
//
// 安全要点（老师傅踩过的坑都在这里）：
//  1. 绝不使用客户端文件名落盘 —— 里面可能是 ../../etc/passwd（路径穿越）；
//  2. 用 crypto/rand 生成文件名，避免同名覆盖与可枚举；
//  3. 校验扩展名 + 大小；生产环境还应读文件头确认真实类型（magic number）；
//  4. 上传目录不要放在可执行路径下，静态服务时禁止目录列表。
func saveUpload(c *gin.Context, field string, imagesOnly bool) (string, error) {
	fh, err := c.FormFile(field)
	if err != nil {
		return "", err
	}
	if fh.Size > maxUploadSize {
		return "", errUploadTooLarge
	}

	ext := strings.ToLower(filepath.Ext(fh.Filename))
	if imagesOnly && !imageExts[ext] {
		return "", errUploadBadType
	}
	if ext == "" {
		ext = ".bin"
	}

	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		return "", err
	}
	name := hex.EncodeToString(buf) + ext

	if err := os.MkdirAll(uploadDir, 0o755); err != nil {
		return "", err
	}
	if err := c.SaveUploadedFile(fh, filepath.Join(uploadDir, name)); err != nil {
		return "", err
	}
	return "/uploads/" + name, nil
}
