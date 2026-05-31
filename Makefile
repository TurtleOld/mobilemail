.PHONY: docs-build docs-serve docs-clean release-tag

docs-build:
	@echo "Генерация документации через Dokka..."
	./gradlew dokkaHtml

docs-serve: docs-build
	@echo "Документация сгенерирована в docs/api/"
	@echo "Откройте docs/api/index.html в браузере"

docs-clean:
	@echo "Очистка сгенерированной документации..."
	rm -rf docs/api

release-tag:
	@if [ -z "$(VERSION)" ]; then \
		echo "Usage: make release-tag VERSION=1.2.3"; \
		exit 1; \
	fi
	git tag "v$(VERSION)"
	git push origin "v$(VERSION)"
