## 产生文件

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Build
        run: |
          mkdir -p dist
          echo "hello" > dist/app.txt

      - name: Upload artifact
        uses: actions/upload-artifact@v4
        with:
          name: build-output
          path: dist/
```

## 使用文件

```yaml
  docker:
    runs-on: ubuntu-latest
    needs: build
    steps:
      - uses: actions/checkout@v4

      - name: Download artifact
        uses: actions/download-artifact@v4
        with:
          name: build-output
          path: dist/

      - run: cat dist/app.txt
```