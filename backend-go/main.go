package main 

import (
	"log"

	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/router"
	"github.com/Sh1tFXXK/easychat-backend/backend-go/internal/store"
)

func main() {
	// In-memory store for the Go rewrite (phase 1). Swap in a MySQL-backed
	// store later once the database layer is wired up.
	userStore := store.NewInMemoryUserStore()
	messageStore := store.NewInMemoryMessageStore()
	favoriteStore := store.NewInMemoryFavoriteStore()

	r := router.SetupRouter(userStore, messageStore, favoriteStore)

	log.Println("easychat-go backend listening on :8080")
	if err := r.Run(":8080"); err != nil {
		log.Fatalf("server failed: %v", err)
	}
}

