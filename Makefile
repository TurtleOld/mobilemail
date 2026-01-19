.PHONY: docs-build docs-serve docs-clean

docs-build:
	@echo "Генерация документации через Dokka..."
	./gradlew dokkaHtml

docs-serve: docs-build
	@echo "Документация сгенерирована в docs/api/"
	@echo "Откройте docs/api/index.html в браузере"
	@if command -v python3 > /dev/null; then \
		echo "Запуск локального сервера на http://localhost:8000"; \
		cd docs/api && python3 -m http.server 8000; \
	else \
		echo "Python не найден. Откройте docs/api/index.html вручную"; \
	fi

docs-clean:
	@echo "Очистка сгенерированной документации..."
	rm -rf docs/api
