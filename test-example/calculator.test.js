const { add, subtract, multiply, divide } = require('./calculator');

describe('Calculator', () => {
  test('add returns correct sum', () => {
    expect(add(2, 3)).toBe(5);
    expect(add(-1, 1)).toBe(0);
  });

  test('subtract returns correct difference', () => {
    expect(subtract(5, 3)).toBe(2);
    expect(subtract(0, 5)).toBe(-5);
  });

  test('multiply returns correct product', () => {
    expect(multiply(3, 4)).toBe(12);
    expect(multiply(-2, 3)).toBe(-6);
  });

  test('divide returns correct quotient', () => {
    expect(divide(10, 2)).toBe(5);
    expect(divide(7, 2)).toBe(3.5);
  });

  test('divide throws on division by zero', () => {
    expect(() => divide(5, 0)).toThrow('Cannot divide by zero');
  });
});

