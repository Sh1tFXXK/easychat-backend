package handler

import (
	"errors"
	"fmt"
	"net/http"
	"strings"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/model"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/response"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
	"github.com/gin-gonic/gin"
	"golang.org/x/crypto/bcrypt"
)

// UserHandler is the Go counterpart of the Java UserController.
type UserHandler struct {
	store   store.UserStore
	friends store.FriendStore
	verify  store.VerifyStore
}

// NewUserHandler creates a UserHandler with its dependencies.
func NewUserHandler(s store.UserStore, friends store.FriendStore, verify store.VerifyStore) *UserHandler {
	return &UserHandler{store: s, friends: friends, verify: verify}
}

// Register godoc
// @Summary      Register a new user
// @Router       /api/user/register [post]
func (h *UserHandler) Register(c *gin.Context) {
	var req model.RegisterRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Error("invalid request body: "+err.Error()))
		return
	}

	exists, err := h.store.Exists(req.Username)
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Error("failed to check username"))
		return
	}
	if exists {
		c.JSON(http.StatusConflict, response.Error("username already exists"))
		return
	}

	hash, err := hashPassword(req.Password)
	if err != nil {
		c.JSON(http.StatusBadRequest, response.ErrorWithCode("invalid password: "+err.Error(), http.StatusBadRequest))
		return
	}

	nickname := req.Nickname
	if strings.TrimSpace(nickname) == "" {
		nickname = req.Username
	}

	user := &model.User{
		Username:     strings.TrimSpace(req.Username),
		PasswordHash: hash,
		Nickname:     nickname,
		Tags:         []string{},
		Status:       1,
	}
	if err := h.store.Create(user); err != nil {
		if errors.Is(err, store.ErrUserAlreadyExists) {
			c.JSON(http.StatusConflict, response.Error("username already exists"))
			return
		}
		c.JSON(http.StatusInternalServerError, response.Error("failed to create user"))
		return
	}

	c.JSON(http.StatusCreated, response.SuccessMsg("user registered", user.ToPublic()))
}

// Login godoc
// @Summary      Log in a user
// @Router       /api/user/login [post]
func (h *UserHandler) Login(c *gin.Context) {
	var req model.LoginRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, response.Error("invalid request body: "+err.Error()))
		return
	}

	user, err := h.store.GetByUsername(req.Username)
	if err != nil {
		c.JSON(http.StatusUnauthorized, response.Error("invalid credentials"))
		return
	}
	// 密码比对必须走 bcrypt（恒定时间），不能用字符串比较。
	if bcrypt.CompareHashAndPassword([]byte(user.PasswordHash), []byte(req.Password)) != nil {
		c.JSON(http.StatusUnauthorized, response.Error("invalid credentials"))
		return
	}

	// 这是骨架期的兼容接口；正式的登录（含 token 签发）走 POST /auth/login。
	c.JSON(http.StatusOK, response.SuccessMsg("login successful, use /auth/login for a token", gin.H{
		"userId": user.ID,
		"user":   user.ToPublic(),
	}))
}

// GetUser godoc
// @Summary      Get user info by id
// @Router       /api/user/:id [get]
func (h *UserHandler) GetUser(c *gin.Context) {
	// 兼容两种取参方式：
	//   GET /api/user/user?id=1   —— Java 版原路径，参数在 query
	//   GET /api/user/1           —— Go 骨架期的 RESTful 写法，参数在 path
	// 先看 path 再看 query，两者都没有才判定为非法。
	rawID := c.Param("id")
	if rawID == "" {
		rawID = c.Query("id")
	}
	var id uint64
	if _, err := fmt.Sscanf(rawID, "%d", &id); err != nil {
		c.JSON(http.StatusBadRequest, response.Error("invalid user id"))
		return
	}

	user, err := h.store.GetByID(id)
	if err != nil {
		if errors.Is(err, store.ErrUserNotFound) {
			c.JSON(http.StatusNotFound, response.Error("user not found"))
			return
		}
		c.JSON(http.StatusInternalServerError, response.Error("failed to get user"))
		return
	}

	c.JSON(http.StatusOK, response.Success(user.ToPublic()))
}

// ValidateUsername godoc
// @Summary      Check whether a username is available
// @Router       /api/user/username/validate [post]
func (h *UserHandler) ValidateUsername(c *gin.Context) {
	var body struct {
		Username string `json:"username" binding:"required"`
	}
	if err := c.ShouldBindJSON(&body); err != nil {
		c.JSON(http.StatusBadRequest, response.Error("invalid request body: "+err.Error()))
		return
	}

	exists, err := h.store.Exists(body.Username)
	if err != nil {
		c.JSON(http.StatusInternalServerError, response.Error("failed to check username"))
		return
	}

	if exists {
		c.JSON(http.StatusOK, response.Error("username already exists"))
		return
	}
	c.JSON(http.StatusOK, response.SuccessMsg("username available", gin.H{"available": true}))
}

// SearchUser godoc
// @Summary      Search users by username prefix
// @Router       /api/user/search [get]
func (h *UserHandler) SearchUser(c *gin.Context) {
	q := c.Query("q")
	if q == "" {
		c.JSON(http.StatusBadRequest, response.Error("query param 'q' is required"))
		return
	}

	// Note: the in-memory store does not support prefix search yet; this
	// endpoint returns the exact match when a MySQL store is wired up.
	user, err := h.store.GetByUsername(q)
	if err != nil {
		if errors.Is(err, store.ErrUserNotFound) {
			c.JSON(http.StatusOK, response.Success([]model.PublicUser{}))
			return
		}
		c.JSON(http.StatusInternalServerError, response.Error("search failed"))
		return
	}

	c.JSON(http.StatusOK, response.Success([]model.PublicUser{user.ToPublic()}))
}
