FROM golang:1.19.5 AS build_base

WORKDIR /tmp/authserver

COPY . .

RUN go mod download && go build -o ./out/authserver ./main.go

FROM debian:bookworm as app

COPY --from=build_base /tmp/authserver/out/authserver /app/authserver

EXPOSE 5001

ENTRYPOINT ["/app/authserver"]
