package main

import (
	"fmt"
	cmAuth "github.com/chartmuseum/auth"
	"github.com/gin-gonic/gin"
	"strings"
	"time"
)

var (
	tokenGenerator    *cmAuth.TokenGenerator
	tokenExpiry       = time.Minute * 5
	requiredGrantType = "client_credentials"
	masterAccessKey   = "MASTERKEY"
	ociToken          string
)

func cmOAuthTokenHandler(c *gin.Context) { //token auth only here
	authHeader := strings.TrimPrefix(c.GetHeader("Authorization"), "Bearer ")

	if authHeader != masterAccessKey {
		c.JSON(401, gin.H{"error": fmt.Sprintf(authHeader)})
		return
	}

	grantType := c.Query("grant_type")
	if grantType != requiredGrantType {
		c.JSON(400, gin.H{"error": fmt.Sprintf("grant_type must equal %s", requiredGrantType)})
		return
	}

	scope := c.Query("scope")
	parts := strings.Split(scope, ":")
	if len(parts) != 3 || parts[0] != cmAuth.AccessEntryType {
		c.JSON(400, gin.H{"error": fmt.Sprintf("scope is missing or invalid")})
		return
	}

	access := []cmAuth.AccessEntry{
		{
			Name:    parts[1],
			Type:    cmAuth.AccessEntryType,
			Actions: strings.Split(parts[2], ","),
		},
	}
	accessToken, err := tokenGenerator.GenerateToken(access, tokenExpiry)
	ociToken = accessToken
	if err != nil {
		c.JSON(500, gin.H{"error": err})
		return
	}
	c.JSON(200, gin.H{"access_token": accessToken})
}

func ociOAuthTokenHandler(c *gin.Context) {
	c.JSON(200, gin.H{"access_token": ociToken})
}

func main() {
	var err error

	privateKey := "/config/server.key"
	publicKey := "/config/server.pem"
	tokenGenerator, err = cmAuth.NewTokenGenerator(&cmAuth.TokenGeneratorOptions{
		PrivateKeyPath: privateKey,
		Audience:       "Authentication",
		Issuer:         "Sample Issuer",
		AddKIDHeader:   true,
	})
	if err != nil {
		panic(err)
	}

	r := gin.Default()
	r.POST("/oauth/token", cmOAuthTokenHandler)
	r.GET("/auth", ociOAuthTokenHandler)
	r.RunTLS(":5001", publicKey, privateKey) // listen and serve on 0.0.0.0:5001
}
