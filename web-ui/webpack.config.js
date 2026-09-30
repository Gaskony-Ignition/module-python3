const path = require("path");

module.exports = (webpackConfigEnv = {}, argv = {}) => {
  const { mode = "development" } = argv;

  const externals = [
    "react",
    "react-dom"
  ];

  return {
    mode,
    entry: {
      Python3IDE: path.join(__dirname, "src/index.tsx")
    },
    output: {
      library: "[name]",
      libraryTarget: "umd",
      umdNamedDefine: true,
      globalObject: 'this',
      filename: "[name].js",
      publicPath: "",
      path: path.resolve(__dirname, "build/generated-resources/mounted/"),
    },
    context: path.resolve(__dirname),
    module: {
      rules: [
        {
          test: /\.css$/,
          use: ["style-loader", "css-loader"],
        },
        {
          test: /\.(ts|tsx)$/,
          use: {
            loader: "ts-loader",
            options: { configFile: "tsconfig.webpack.json" }
          },
          exclude: /node_modules/,
        },
        {
          test: /\.(js|jsx)$/,
          use: "ts-loader",
          exclude: /node_modules/,
        },
        {
          test: /\.(woff|woff2|eot|ttf|otf)$/i,
          type: 'asset/inline',
        },
      ],
    },
    devtool: mode === "development" ? "source-map" : false,
    plugins: [],
    resolve: {
      extensions: [".ts", ".tsx", ".js", ".jsx", ".css"],
    },
    externals,
    performance: { hints: false }
  };
};
