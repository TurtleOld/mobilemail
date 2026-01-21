.PHONY: docs-build docs-serve docs-clean

docs-build:
	@echo "Генерация документации через Dokka..."
	./gradlew dokkaHtml

docs-serve: docs-build
	@echo "Документация сгенерирована в docs/api/"
	@echo "Откройте docs/api/index.html в браузере"

docs-clean:
	@echo "Очистка сгенерированной документации..."
	rm -rf docs/api
